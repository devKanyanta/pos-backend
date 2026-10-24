package com.devkanyanta.pos.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.devkanyanta.pos.report.dto.ProductSummary;

import lombok.Builder;
import lombok.Getter;

/** End-of-day (Z) report: sales, VAT, tenders, expected vs declared, ZRA status. */
@Getter
@Builder
public class ZReportResponse {

    private LocalDate businessDate;

    private LocalDateTime generatedAt;

    private String businessName;

    private String tpin;

    private String accountId;

    private SalesSection sales;

    private List<VatLine> vat;

    private RefundSection refunds;

    private List<TenderLine> tenders;

    private Totals totals;

    private BigDecimal openingFloat;

    /** Cash that should be in the drawer: opening float + cash received - cash refunds. */
    private BigDecimal expectedCashInDrawer;

    /** BALANCED, SHORT, OVER, or NOT_RECONCILED when no cash-up was submitted yet. */
    private String reconciliationStatus;

    private String reconciledBy;

    private LocalDateTime reconciledAt;

    private String notes;

    private ZraSection zra;

    private List<ProductSummary> topProducts;

    @Getter
    @Builder
    public static class SalesSection {

        private long count;

        private long voidedCount;

        private long refundedCount;

        private long offlineCount;

        private long needsReviewCount;

        private BigDecimal subtotal;

        private BigDecimal discounts;

        private BigDecimal vat;

        /** Sum of sale totals (VAT inclusive) for sales made on the day. */
        private BigDecimal grossSales;

        /** Balance still due on the day's sales. */
        private BigDecimal outstanding;
    }

    @Getter
    @Builder
    public static class VatLine {

        private String taxType;

        private String label;

        private BigDecimal rate;

        private BigDecimal taxableAmount;

        private BigDecimal vatAmount;
    }

    @Getter
    @Builder
    public static class RefundSection {

        private long count;

        private BigDecimal total;

        private BigDecimal vat;
    }

    @Getter
    @Builder
    public static class TenderLine {

        private Long paymentMethodId;

        private String name;

        private String code;

        private String type;

        private long transactionCount;

        private BigDecimal received;

        private BigDecimal refunded;

        /** received - refunded (+ opening float for cash). */
        private BigDecimal expected;

        /** null until the cash-up is submitted. */
        private BigDecimal declared;

        private BigDecimal variance;

        private boolean flagged;
    }

    @Getter
    @Builder
    public static class Totals {

        private BigDecimal received;

        private BigDecimal refunded;

        private BigDecimal expected;

        private BigDecimal declared;

        private BigDecimal variance;
    }

    @Getter
    @Builder
    public static class ZraSection {

        private long signed;

        private long pending;

        private long failed;

        private long notReported;
    }
}
