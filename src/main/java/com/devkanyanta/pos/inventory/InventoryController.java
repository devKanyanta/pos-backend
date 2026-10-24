package com.devkanyanta.pos.inventory;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.inventory.InventoryDtos.AdjustmentReport;
import com.devkanyanta.pos.inventory.InventoryDtos.CogsReport;
import com.devkanyanta.pos.inventory.InventoryDtos.DepletionItem;
import com.devkanyanta.pos.inventory.InventoryDtos.MoversReport;
import com.devkanyanta.pos.inventory.InventoryDtos.PurchaseReport;
import com.devkanyanta.pos.inventory.InventoryDtos.StockLevel;
import com.devkanyanta.pos.inventory.InventoryDtos.StockSummary;
import com.devkanyanta.pos.inventory.InventoryDtos.ValuationReport;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;

/** Stock positions, movement history and inventory reports. */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final String VIEW = "hasAuthority('" + PermissionConstants.INVENTORY_VIEW + "')";
    private static final String REPORT = "hasAuthority('" + PermissionConstants.REPORT_VIEW + "')";
    private static final String DATE = "yyyy-MM-dd";

    private final InventoryQueryService queryService;

    private final InventoryReportService reportService;

    @GetMapping("/stock")
    @PreAuthorize(VIEW)
    public PageResponse<StockLevel> stockLevels(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return queryService.stockLevels(q, categoryId, status, active, page, size, sort);
    }

    @GetMapping("/stock/summary")
    @PreAuthorize(VIEW)
    public StockSummary stockSummary(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return queryService.summary(categoryId);
    }

    @GetMapping("/movements")
    @PreAuthorize(VIEW)
    public PageResponse<InventoryMovementResponse> movements(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "referenceType", required = false) String referenceType,
            @RequestParam(value = "performedBy", required = false) String performedBy,
            @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = DATE) LocalDate to,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return queryService.movements(productId, type, reason, referenceType, performedBy, from, to, page, size, sort);
    }

    // ---- Reports (REPORT_VIEW and the SME plan) ----

    @GetMapping("/reports/valuation")
    @PreAuthorize(REPORT)
    public ValuationReport valuation(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return reportService.valuation(categoryId);
    }

    @GetMapping("/reports/cogs")
    @PreAuthorize(REPORT)
    public CogsReport cogs(
            @RequestParam("from") @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(pattern = DATE) LocalDate to,
            @RequestParam(value = "categoryId", required = false) Long categoryId
    ) {
        return reportService.cogs(from, to, categoryId);
    }

    @GetMapping("/reports/movers")
    @PreAuthorize(REPORT)
    public MoversReport movers(
            @RequestParam("from") @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(pattern = DATE) LocalDate to,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return reportService.movers(from, to, direction, limit);
    }

    @GetMapping("/reports/adjustments")
    @PreAuthorize(REPORT)
    public AdjustmentReport adjustments(
            @RequestParam("from") @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(pattern = DATE) LocalDate to
    ) {
        return reportService.adjustments(from, to);
    }

    @GetMapping("/reports/purchases")
    @PreAuthorize(REPORT)
    public PurchaseReport purchases(
            @RequestParam("from") @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(pattern = DATE) LocalDate to,
            @RequestParam(value = "supplierId", required = false) Long supplierId
    ) {
        return reportService.purchases(from, to, supplierId);
    }

    @GetMapping("/reports/depletion")
    @PreAuthorize(REPORT)
    public List<DepletionItem> depletion(
            @RequestParam("from") @DateTimeFormat(pattern = DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(pattern = DATE) LocalDate to,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return reportService.depletion(from, to, categoryId, limit);
    }
}
