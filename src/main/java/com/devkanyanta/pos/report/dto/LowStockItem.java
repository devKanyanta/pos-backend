package com.devkanyanta.pos.report.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LowStockItem {

    private Long productId;

    private String productName;

    private Integer stockQuantity;

    private Integer reorderLevel;
}
