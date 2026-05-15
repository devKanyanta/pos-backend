package com.devkanyanta.pos.product.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private Long id;

    private String name;

    private String barcode;

    private String sku;

    private BigDecimal price;

    private BigDecimal costPrice;

    private Integer stockQuantity;

    private Boolean active;

    private Long categoryId;

    private String categoryName;
}
