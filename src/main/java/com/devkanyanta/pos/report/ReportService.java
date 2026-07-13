package com.devkanyanta.pos.report;

import java.time.LocalDate;

import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;

public interface ReportService {

    DailySalesReport getDailySalesReport(LocalDate date);

    SalesSummaryReport getSalesSummary(LocalDate from, LocalDate to);

    InventoryValueReport getInventoryValue();

    java.util.List<com.devkanyanta.pos.report.dto.ProductSummary> getTopProducts(
            LocalDate from, LocalDate to, int limit
    );
}
