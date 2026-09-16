package com.telusko.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOrderResponse {
    private String razorpayOrderId;
    private Long orderId;
    private String amount;
    private String currency;
    private String keyId;
}
