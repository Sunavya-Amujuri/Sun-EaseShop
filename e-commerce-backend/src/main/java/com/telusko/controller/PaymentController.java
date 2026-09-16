package com.telusko.controller;

import com.telusko.dto.ApiMessage;
import com.telusko.dto.PaymentOrderResponse;
import com.telusko.dto.VerifyPaymentRequest;
import com.telusko.service.PaymentService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/ecommerce/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentOrderResponse> createOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(paymentService.createOrder(user.getUsername(), orderId));
    }

    @PostMapping("/verify")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> verify(
            @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(paymentService.verifyPayment(user.getUsername(), request));
    }

    @PostMapping("/failure/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> failure(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(paymentService.handleFailure(user.getUsername(), orderId, reason));
    }

    @PostMapping("/cancel/{orderId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiMessage> cancel(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(paymentService.cancelPayment(user.getUsername(), orderId));
    }
}