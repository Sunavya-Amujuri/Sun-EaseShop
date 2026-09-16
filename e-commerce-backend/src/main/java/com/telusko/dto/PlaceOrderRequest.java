package com.telusko.dto;

import lombok.Data;

@Data
public class PlaceOrderRequest {

    private Long addressId;
    private String paymentMethod;  // "COD" or "ONLINE"
    private String couponCode;
}
