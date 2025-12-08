package com.axon.core_service.service;

import com.axon.core_service.domain.coupon.Coupon;
import com.axon.core_service.domain.dto.coupon.CouponRequest;
import com.axon.core_service.domain.dto.coupon.CouponResponse;
import com.axon.core_service.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponRepository couponRepository;

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
}
