package com.devkanyanta.pos.report.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LowStockItem {

    private String productName;

    private Integer stockQuantity;
}
