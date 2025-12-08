package com.axon.core_service.controller;

import com.axon.core_service.domain.dto.coupon.CouponRequest;
import com.axon.core_service.domain.dto.coupon.CouponResponse;
import com.axon.core_service.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    @GetMapping
    public ResponseEntity<List<CouponResponse>> getAllCoupons() {
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

    @PostMapping
    public ResponseEntity<Long> createCoupon(@RequestBody CouponRequest request) {
        Long couponId = couponService.createCoupon(request);
        return ResponseEntity.ok(couponId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updateCoupon(@PathVariable Long id, @RequestBody CouponRequest request) {
        Long couponId = couponService.updateCoupon(id, request);
        return ResponseEntity.ok(couponId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok().build();
    }
}
