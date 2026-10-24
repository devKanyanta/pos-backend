package com.devkanyanta.pos.report;

import java.time.LocalDate;
import java.util.List;

import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.ProductSummary;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;
import com.devkanyanta.pos.report.dto.TodaySummary;

public interface ReportService {

    DailySalesReport getDailySalesReport(LocalDate date);

    SalesSummaryReport getSalesSummary(LocalDate from, LocalDate to);

    InventoryValueReport getInventoryValue();

    List<ProductSummary> getTopProducts(LocalDate from, LocalDate to, int limit);

    TodaySummary getTodaySummary();
}
