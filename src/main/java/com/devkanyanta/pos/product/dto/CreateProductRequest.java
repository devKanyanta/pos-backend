package com.devkanyanta.pos.product.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProductRequest {

    private String name;

    private String description;

    /** Unit of measurement (see /units). */
    private Long unitId;

    private String barcode;

    private String sku;

    private BigDecimal price;

    private BigDecimal costPrice;

    private Integer stockQuantity;

    private BigDecimal vatRate;

    private Boolean trackInventory;

    private Long categoryId;

    private Integer reorderLevel;

    /** ZRA VAT category: A, B, C1, C2, C3, D, E or RVAT. */
    private String zraTaxType;

    private String zraItemCode;

    private String zraItemClassCode;

    /** 1 = raw material, 2 = finished product, 3 = service. */
    private String zraItemTypeCode;

    private String zraOriginCountryCode;

    private String zraPackagingUnitCode;

    private String zraQuantityUnitCode;
}
