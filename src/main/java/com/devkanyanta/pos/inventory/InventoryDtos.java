package com.devkanyanta.pos.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for the stock-level and report endpoints. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** One product's stock position. {@code status}: IN_STOCK, LOW_STOCK, OUT_OF_STOCK (negative balances included). */
    public record StockLevel(Long productId, String name, String sku, String barcode, Long categoryId,
                             String categoryName, String unitCode, boolean active, int stockQuantity,
                             int reorderLevel, String status, boolean negative, BigDecimal averageCost,
                             BigDecimal stockValue, BigDecimal price, BigDecimal retailValue) {
    }

    public record StockSummary(long trackedProducts, long inStock, long lowStock, long outOfStock,
                               BigDecimal stockValue, BigDecimal retailValue) {
    }

    // ---- Valuation ----

    public record ValuationGroup(Long categoryId, String categoryName, long products, long units,
                                 BigDecimal costValue, BigDecimal retailValue) {
    }

    public record ValuationReport(String method, long products, long units, BigDecimal costValue,
                                  BigDecimal retailValue, BigDecimal potentialMargin, List<ValuationGroup> byCategory) {
    }

    // ---- Cost of goods sold ----

    public record CogsGroup(Long categoryId, String categoryName, long unitsSold, BigDecimal revenue,
                            BigDecimal costOfGoodsSold, BigDecimal grossProfit) {
    }

    public record CogsReport(LocalDate from, LocalDate to, String method, long unitsSold, long unitsRefunded,
                             BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit,
                             BigDecimal marginPercent, long linesWithoutCost, List<CogsGroup> byCategory) {
    }

    // ---- Movers ----

    public record MoverItem(Long productId, String name, String sku, int stockQuantity, long unitsSold,
                            BigDecimal revenue) {
    }

    public record MoversReport(LocalDate from, LocalDate to, String direction, List<MoverItem> items) {
    }

    // ---- Adjustments and counts ----

    public record AdjustmentGroup(String movementType, String reason, long movements, long unitsIn, long unitsOut,
                                  BigDecimal netValue) {
    }

    public record AdjustmentReport(LocalDate from, LocalDate to, List<AdjustmentGroup> groups,
                                   BigDecimal netValue, long committedCounts, BigDecimal countNetVarianceValue) {
    }

    // ---- Purchases ----

    public record PurchaseGroup(Long supplierId, String supplierName, long receipts, long units,
                                BigDecimal receivedCost, long returns, BigDecimal returnedCredit) {
    }

    public record PurchaseReport(LocalDate from, LocalDate to, long receipts, long units, BigDecimal receivedCost,
                                 BigDecimal returnedCredit, List<PurchaseGroup> bySupplier) {
    }

    // ---- Sales depletion ----

    public record DepletionItem(Long productId, String name, String sku, long sold, long voided, long returned,
                                long netDepleted, int stockQuantity) {
    }
}
