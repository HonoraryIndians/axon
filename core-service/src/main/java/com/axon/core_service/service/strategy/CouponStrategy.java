package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.coupon.Coupon;
import com.axon.core_service.domain.coupon.UserCoupon;
import com.axon.core_service.repository.CouponRepository;
import com.axon.core_service.repository.UserCouponRepository;
import com.axon.core_service.service.batch.BatchStrategy;
import com.axon.messaging.CampaignActivityType;
import com.axon.messaging.dto.CampaignActivityKafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStrategy implements BatchStrategy {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;

    @Override
    public CampaignActivityType getType() {
        return CampaignActivityType.COUPON;
    }

    @Override
    @Transactional
    public void process(CampaignActivityKafkaProducerDto message) {
        processBatch(List.of(message));
    }

    @Override
    @Transactional
    public void processBatch(List<CampaignActivityKafkaProducerDto> messages) {
        log.info("Processing Coupon batch size: {}", messages.size());

        // 1. 관련된 Coupon ID 목록 추출 (ProductId 필드 재활용)
        List<Long> couponIds = messages.stream()
                .map(CampaignActivityKafkaProducerDto::getProductId)
                .distinct()
                .collect(Collectors.toList());

        // 2. Coupon 엔티티 조회 (Bulk Read)
        Map<Long, Coupon> couponMap = couponRepository.findAllById(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        // 3. UserCoupon 엔티티 생성 (중복 체크 포함)
        List<UserCoupon> userCoupons = messages.stream()
                .map(msg -> {
                    Long couponId = msg.getProductId();
                    Long userId = msg.getUserId();
                    Coupon coupon = couponMap.get(couponId);

                    if (coupon == null) {
                        log.warn("Coupon not found for ID: {}", couponId);
                        return null;
                    }

                    // DB 레벨 중복 체크 (이미 발급된 경우 스킵)
                    // 주의: 대량 배치 시 이 부분이 병목이 될 수 있으므로,
                    // 실제로는 INSERT IGNORE나 복합 Unique Key로 DB에서 막는 게 더 빠름.
                    if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                        log.info("User {} already has coupon {}", userId, couponId);
                        return null;
                    }

                    return UserCoupon.builder()
                            .userId(userId)
                            .coupon(coupon)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        // 4. Bulk Insert
        if (!userCoupons.isEmpty()) {
            userCouponRepository.saveAll(userCoupons);
            log.info("Saved {} user coupons", userCoupons.size());
        }
    }
}
