package com.devkanyanta.pos.report;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.report.dto.DailySalesReport;
import com.devkanyanta.pos.report.dto.InventoryValueReport;
import com.devkanyanta.pos.report.dto.SalesSummaryReport;
import com.devkanyanta.pos.report.dto.ProductSummary;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales/daily")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public DailySalesReport getDailySalesReport(
            @RequestParam("date") LocalDate date
    ) {
        return reportService.getDailySalesReport(date);
    }

    @GetMapping("/sales/summary")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public SalesSummaryReport getSalesSummary(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to
    ) {
        return reportService.getSalesSummary(from, to);
    }

    @GetMapping("/products/top")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public List<ProductSummary> getTopProducts(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return reportService.getTopProducts(from, to, limit);
    }

    @GetMapping("/inventory/value")
    @PreAuthorize("hasAuthority('" + PermissionConstants.REPORT_VIEW + "')")
    public InventoryValueReport getInventoryValue() {
        return reportService.getInventoryValue();
    }
}
