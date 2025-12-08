package com.axon.core_service.domain.coupon;

import com.axon.core_service.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="user_coupons", indexes = {
        @Index(name="idx_user_coupon_user_id", columnList="user_id")
})
@Getter
@NoArgsConstructor
public class UserCoupon extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="coupon_id", nullable = false)
    private Coupon coupon;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Builder
    public UserCoupon(Long userId, Coupon coupon) {
        this.userId = userId;
        this.coupon = coupon;
        this.status = CouponStatus.ISSUED;
    }

    public void use() {
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }
}
