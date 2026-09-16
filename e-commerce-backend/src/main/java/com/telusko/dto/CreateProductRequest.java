package com.telusko.dto;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {

    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Long category;
}