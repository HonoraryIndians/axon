package com.axon.core_service.repository;

import com.axon.core_service.domain.coupon.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @org.springframework.data.jpa.repository.Query("SELECT uc.coupon.id FROM UserCoupon uc WHERE uc.userId = :userId")
    java.util.List<Long> findAllCouponIdsByUserId(Long userId);
}
