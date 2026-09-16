package com.telusko.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponseDto {

    private Long id;
    private String orderNumber;
    private String status;
    private String paymentMethod;

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;

    private LocalDateTime createdAt;

    private String shippingName;
    private String shippingLine1;
    private String shippingLine2;
    private String shippingCity;
    private String shippingState;
    private String shippingPinCode;
    private String shippingCountry;
    private String shippingLabel;

    private List<OrderItemResponseDto> items;
}
