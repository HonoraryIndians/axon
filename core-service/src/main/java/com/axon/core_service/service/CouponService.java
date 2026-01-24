package com.axon.core_service.service;

import com.axon.core_service.domain.coupon.Coupon;
import com.axon.core_service.domain.coupon.CouponStatus;
import com.axon.core_service.domain.coupon.UserCoupon;
import com.axon.core_service.domain.dto.coupon.ApplicableCouponDto;
import com.axon.core_service.domain.dto.coupon.CouponRequest;
import com.axon.core_service.domain.dto.coupon.CouponResponse;
import com.axon.core_service.repository.CouponRepository;
import com.axon.core_service.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(CouponResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long createCoupon(CouponRequest request) {
        validateCouponRequest(request);

        Coupon coupon = Coupon.builder()
                .name(request.getCouponName())
                .discountAmount(request.getDiscountAmount())
                .discountRate(request.getDiscountRate())
                .minOrderAmount(request.getMinOrderAmount())
                .targetCategory(request.getTargetCategory())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        couponRepository.save(coupon);
        return coupon.getId();
    }

    private void validateCouponRequest(CouponRequest request) {
        if (request.getCouponName() == null || request.getCouponName().trim().isEmpty()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다.");
        }
        if (request.getDiscountAmount() == null && request.getDiscountRate() == null) {
            throw new IllegalArgumentException("할인 금액 또는 할인율 중 하나는 필수입니다.");
        }
        if (request.getDiscountAmount() != null && request.getDiscountRate() != null) {
            throw new IllegalArgumentException("할인 금액과 할인율을 동시에 설정할 수 없습니다.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("시작 날짜와 종료 날짜는 필수입니다.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 빠를 수 없습니다.");
        }
    }

    @Transactional
    public Long updateCoupon(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));

        validateCouponRequest(request);

        coupon.update(
                request.getCouponName(),
                request.getDiscountAmount(),
                request.getDiscountRate(),
                request.getMinOrderAmount(),
                request.getTargetCategory(),
                request.getStartDate(),
                request.getEndDate());

        return coupon.getId();
    }

    @Transactional
    public void deleteCoupon(Long id) {
        couponRepository.deleteById(id);
    }

    /**
     * 사용자가 특정 상품에 적용 가능한 쿠폰 목록 조회
     *
     * @param userId           사용자 ID
     * @param productPrice     상품 가격
     * @param productCategory  상품 카테고리
     * @return 적용 가능한 쿠폰 리스트
     */
    public List<ApplicableCouponDto> getApplicableCoupons(Long userId, BigDecimal productPrice, String productCategory) {
        // 1. 사용자가 보유한 쿠폰 조회
        List<UserCoupon> userCoupons = userCouponRepository.findAllByUserId(userId);

        LocalDateTime now = LocalDateTime.now();

        // 2. 적용 가능한 쿠폰만 필터링
        return userCoupons.stream()
                .filter(uc -> isApplicable(uc, productPrice, productCategory, now))
                .map(uc -> toDto(uc, productPrice))
                .collect(Collectors.toList());
    }

    /**
     * 쿠폰 적용 가능 여부 검증
     */
    private boolean isApplicable(UserCoupon userCoupon, BigDecimal productPrice, String productCategory, LocalDateTime now) {
        Coupon coupon = userCoupon.getCoupon();

        // 1. 이미 사용된 쿠폰은 제외
        if (userCoupon.getStatus() != CouponStatus.ISSUED) {
            log.debug("Coupon {} already used", coupon.getId());
            return false;
        }

        // 2. 유효 기간 체크
        if (now.isBefore(coupon.getStartDate()) || now.isAfter(coupon.getEndDate())) {
            log.debug("Coupon {} expired or not started", coupon.getId());
            return false;
        }

        // 3. 최소 주문 금액 체크
        if (coupon.getMinOrderAmount() != null && productPrice.compareTo(coupon.getMinOrderAmount()) < 0) {
            log.debug("Coupon {} requires min order amount {}, but product price is {}",
                    coupon.getId(), coupon.getMinOrderAmount(), productPrice);
            return false;
        }

        // 4. 카테고리 제한 체크
        if (coupon.getTargetCategory() != null && !coupon.getTargetCategory().isEmpty()) {
            if (!coupon.getTargetCategory().equalsIgnoreCase(productCategory)) {
                log.debug("Coupon {} requires category {}, but product is {}",
                        coupon.getId(), coupon.getTargetCategory(), productCategory);
                return false;
            }
        }

        return true;
    }

    /**
     * UserCoupon을 DTO로 변환하면서 할인 금액 계산
     */
    private ApplicableCouponDto toDto(UserCoupon userCoupon, BigDecimal productPrice) {
        Coupon coupon = userCoupon.getCoupon();
        BigDecimal calculatedDiscount = calculateDiscount(coupon, productPrice);

        return ApplicableCouponDto.builder()
                .userCouponId(userCoupon.getId())
                .couponId(coupon.getId())
                .couponName(coupon.getCouponName())
                .discountAmount(coupon.getDiscountAmount())
                .discountRate(coupon.getDiscountRate())
                .minOrderAmount(coupon.getMinOrderAmount())
                .targetCategory(coupon.getTargetCategory())
                .calculatedDiscount(calculatedDiscount)
                .build();
    }

    /**
     * 실제 할인 금액 계산
     */
    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal productPrice) {
        BigDecimal discount = BigDecimal.ZERO;

        // 정액 할인
        if (coupon.getDiscountAmount() != null) {
            discount = coupon.getDiscountAmount();
        }
        // 정률 할인
        else if (coupon.getDiscountRate() != null) {
            discount = productPrice
                    .multiply(BigDecimal.valueOf(coupon.getDiscountRate()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        }

        // 할인 금액이 상품 가격을 초과할 수 없음
        if (discount.compareTo(productPrice) > 0) {
            discount = productPrice;
        }

        return discount;
    }

    /**
     * 쿠폰 사용 처리
     *
     * @param userCouponId 사용할 UserCoupon ID
     * @param userId 사용자 ID (검증용)
     */
    @Transactional
    public void useCoupon(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("UserCoupon not found: " + userCouponId));

        // 소유자 확인
        if (!userCoupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("UserCoupon does not belong to user: " + userId);
        }

        // 이미 사용된 쿠폰인지 확인
        if (userCoupon.getStatus() != CouponStatus.ISSUED) {
            throw new IllegalStateException("Coupon already used or invalid");
        }

        // 쿠폰 사용 처리
        userCoupon.use();
        log.info("Coupon {} used by user {}", userCouponId, userId);
    }
}
