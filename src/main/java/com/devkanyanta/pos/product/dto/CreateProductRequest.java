package com.devkanyanta.pos.product.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductRequest {

    private String name;

    private String barcode;

    private String sku;

    private BigDecimal price;

    private BigDecimal costPrice;

    private Integer stockQuantity;

    private Long categoryId;
}