package com.telusko.dto;

public record ReturnEligibilityResponse(
        Long orderId,
        String orderStatus,
        boolean eligible,
        String reason,
        String explanation
) {}
