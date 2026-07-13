package com.devkanyanta.pos.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SalesSummaryReport {

    private LocalDate from;

    private LocalDate to;

    private Long totalSales;

    private BigDecimal totalRevenue;

    private BigDecimal totalVat;

    private BigDecimal averageOrderValue;
}
