package com.axon.core_service.domain.dto.coupon;

import com.axon.core_service.domain.coupon.Coupon;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class CouponResponse {
    private final Long id;
    private final String couponName;
    private final BigDecimal discountAmount;
    private final Integer discountRate;
    private final BigDecimal minOrderAmount;
    private final String targetCategory;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;

    public CouponResponse(Coupon coupon) {
        this.id = coupon.getId();
        this.couponName = coupon.getCouponName();
        this.discountAmount = coupon.getDiscountAmount();
        this.discountRate = coupon.getDiscountRate();
        this.minOrderAmount = coupon.getMinOrderAmount();
        this.targetCategory = coupon.getTargetCategory();
        this.startDate = coupon.getStartDate();
        this.endDate = coupon.getEndDate();
    }
}
