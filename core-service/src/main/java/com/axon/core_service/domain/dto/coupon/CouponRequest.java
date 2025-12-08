package com.axon.core_service.domain.dto.coupon;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CouponRequest {
    private String couponName;
    private BigDecimal discountAmount;
    private Integer discountRate;
    private BigDecimal minOrderAmount;
    private String targetCategory;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
