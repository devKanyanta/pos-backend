package com.devkanyanta.pos.report.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductSummary {

    private String productName;

    private Long totalQuantity;

    private BigDecimal totalRevenue;
}
