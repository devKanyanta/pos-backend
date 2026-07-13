package com.devkanyanta.pos.report.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryValueReport {

    private Long totalProducts;

    private BigDecimal totalStockValue;

    private BigDecimal totalSellingValue;

    private List<LowStockItem> lowStockItems;
}
