package com.telusko.controller;

import com.telusko.dto.CartResponseDto;
import com.telusko.model.Coupon;
import com.telusko.service.CouponService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/v1/ecommerce")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon management and usage")
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/coupons")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<Coupon>> getAllCoupons(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(couponService.getAllCoupons(page, limit));
    }

    @PostMapping("/coupons")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Coupon> createCoupon(
            @RequestBody Coupon coupon
    ) {
        Coupon created = couponService.createCoupon(coupon);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/coupons/{couponId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Coupon> getCouponById(
            @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(couponService.getCouponById(couponId));
    }

    @DeleteMapping("/coupons/{couponId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> deleteCoupon(
            @PathVariable Long couponId
    ) {
        couponService.deleteCoupon(couponId);
        return ResponseEntity.ok(Map.of("message", "Coupon deleted successfully"));
    }

    @PatchMapping("/coupons/{couponId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Coupon> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody Coupon coupon
    ) {
        Coupon updated = couponService.updateCoupon(couponId, coupon);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/coupons/status/{couponId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Coupon> updateCouponStatus(
            @PathVariable Long couponId,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean isActive = Boolean.TRUE.equals(body.get("isActive"));
        Coupon updated = couponService.updateCouponActiveStatus(couponId, isActive);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/coupons/customer/available")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<Coupon>> getAvailableCouponsForCustomer(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        Page<Coupon> result =
                couponService.getAvailableCouponsForCustomer(
                        currentUser.getUsername(), page, limit);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/coupons/c/apply")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CartResponseDto> applyCoupon(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String couponCode = body.get("couponCode");
        CartResponseDto cart =
                couponService.applyCoupon(currentUser.getUsername(), couponCode);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/coupons/c/remove")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CartResponseDto> removeCoupon(
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        CartResponseDto cart =
                couponService.removeCoupon(currentUser.getUsername());
        return ResponseEntity.ok(cart);
    }
}
