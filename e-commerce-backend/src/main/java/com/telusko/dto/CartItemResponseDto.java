package com.telusko.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CartItemResponseDto {
    private Long productId;
    private String productName;
    private String mainImage;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}