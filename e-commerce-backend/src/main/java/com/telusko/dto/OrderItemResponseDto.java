package com.telusko.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponseDto {

    private Long productId;
    private String productName;
    private String mainImage;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
