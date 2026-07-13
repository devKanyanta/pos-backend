package com.devkanyanta.pos.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailySalesReport {

    private String date;

    private Long totalSales;

    private BigDecimal totalRevenue;

    private BigDecimal totalVat;

    private Map<String, BigDecimal> paymentMethodBreakdown;

    private List<ProductSummary> topProducts;
}
