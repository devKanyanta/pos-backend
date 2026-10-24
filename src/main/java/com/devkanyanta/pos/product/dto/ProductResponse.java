package com.devkanyanta.pos.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private Long id;

    private String name;

    private String description;

    private Long unitId;

    private String unitCode;

    private String barcode;

    private String sku;

    private BigDecimal price;

    /** Latest purchase cost. */
    private BigDecimal costPrice;

    /** Weighted-average cost of stock on hand (used for valuation and cost of goods sold). */
    private BigDecimal averageCost;

    private Integer stockQuantity;

    private Boolean active;

    private BigDecimal vatRate;

    private Boolean trackInventory;

    private Long categoryId;

    private String categoryName;

    private Integer reorderLevel;

    /** True when stock is at or below the reorder level (dashboard low-stock flag). */
    private Boolean lowStock;

    private String zraTaxType;

    private String zraItemCode;

    private String zraItemClassCode;

    private String zraItemTypeCode;

    private String zraOriginCountryCode;

    private String zraPackagingUnitCode;

    private String zraQuantityUnitCode;

    /** All ZRA fields required for a Smart Invoice are present. */
    private Boolean zraMapped;

    private Boolean zraRegistered;

    private LocalDateTime updatedAt;
}
