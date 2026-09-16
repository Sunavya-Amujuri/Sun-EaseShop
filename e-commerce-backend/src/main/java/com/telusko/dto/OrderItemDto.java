package com.telusko.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItemDto {
    private Long productId;
    private String productName;
    private String mainImage;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
