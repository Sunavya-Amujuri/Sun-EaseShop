package com.telusko.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQty;
    private String sku;
    private Boolean active;

    private String mainImage;
    private List<String> subImages;

    private Long categoryId;
    private String categoryName;
}
