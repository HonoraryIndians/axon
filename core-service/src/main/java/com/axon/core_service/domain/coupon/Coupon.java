package com.axon.core_service.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_name", nullable = false)
    String couponName;

    @Column(name = "discount_amount") // 정액 할인
    private BigDecimal discountAmount;

    @Column(name = "discount_rate") // 정률 할인 (%)
    private Integer discountRate;

    @Column(name = "minOrderAmount") // 최소 주문 금액
    private BigDecimal minOrderAmount;

    @Column(name = "target_category")
    private String targetCategory; // 적용 가능 카테고리 (Nullable)

    @Column(name = "start_date", nullable = false)
    private java.time.LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private java.time.LocalDateTime endDate;

    @Builder
    public Coupon(String name, java.math.BigDecimal discountAmount, Integer discountRate,
            java.math.BigDecimal minOrderAmount, String targetCategory,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        this.couponName = name;
        this.discountAmount = discountAmount;
        this.discountRate = discountRate;
        this.minOrderAmount = minOrderAmount;
        this.targetCategory = targetCategory;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void update(String name, BigDecimal discountAmount, Integer discountRate,
            BigDecimal minOrderAmount, String targetCategory,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        this.couponName = name;
        this.discountAmount = discountAmount;
        this.discountRate = discountRate;
        this.minOrderAmount = minOrderAmount;
        this.targetCategory = targetCategory;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
