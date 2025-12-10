package com.axon.core_service.domain.dto.coupon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicableCouponDto {
    private Long userCouponId; // UserCoupon ID (사용 시 필요)
    private Long couponId;
    private String couponName;
    private BigDecimal discountAmount; // 정액 할인
    private Integer discountRate; // 정률 할인 (%)
    private BigDecimal minOrderAmount; // 최소 주문 금액
    private String targetCategory; // 적용 가능 카테고리

    // Calculated fields for UI
    private BigDecimal calculatedDiscount; // 실제 할인 금액 (계산된 값)
}
