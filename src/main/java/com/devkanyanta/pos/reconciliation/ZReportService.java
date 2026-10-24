package com.devkanyanta.pos.reconciliation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.merchant.MerchantProfileEntity;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentMethodEntity;
import com.devkanyanta.pos.payment.PaymentMethodRepository;
import com.devkanyanta.pos.payment.PaymentMethodType;
import com.devkanyanta.pos.payment.PaymentRepository;
import com.devkanyanta.pos.reconciliation.dto.ZReportResponse;
import com.devkanyanta.pos.report.ReportService;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleEntity.SaleSource;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundPaymentEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.csv.CsvParser;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.pdf.SimplePdfWriter;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;
import com.devkanyanta.pos.zra.ZraTaxType;

import lombok.RequiredArgsConstructor;

/**
 * Builds the end-of-day Z-report: sales and VAT for the business day, money received per tender,
 * refunds paid out per tender, the expected closing balance per tender, and (once the cash-up is
 * submitted) declared amounts and variances. Exports to CSV, PDF and a WhatsApp text summary.
 */
@Service
@RequiredArgsConstructor
public class ZReportService {

    private final SaleRepository saleRepository;

    private final PaymentRepository paymentRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final SaleRefundRepository saleRefundRepository;

    private final ReconciliationRepository reconciliationRepository;

    private final ZraSubmissionRepository zraSubmissionRepository;

    private final MerchantProfileService merchantProfileService;

    private final ReportService reportService;

    private final PosProperties properties;

    private final Clock clock;

    /** Expected amounts per tender for a day. */
    record TenderTotals(PaymentMethodEntity method, long count, BigDecimal received, BigDecimal refunded, BigDecimal expected) {
    }

    @Transactional(readOnly = true)
    public ZReportResponse generate(LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now(clock);
        validateDate(businessDate);

        LocalDateTime start = businessDate.atStartOfDay();
        LocalDateTime end = businessDate.plusDays(1).atStartOfDay();

        MerchantProfileEntity merchant = merchantProfileService.getOrCreate();
        ReconciliationEntity reconciliation = reconciliationRepository.findByBusinessDate(businessDate).orElse(null);
        BigDecimal openingFloat = reconciliation != null ? Money.of(reconciliation.getOpeningFloat()) : Money.ZERO;

        // ---- Tenders ----
        List<TenderTotals> tenderTotals = computeTenders(start, end, openingFloat);
        Map<Long, ReconciliationLineEntity> declaredByMethod = new HashMap<>();
        if (reconciliation != null) {
            reconciliation.getLines().forEach(line -> declaredByMethod.put(line.getPaymentMethod().getId(), line));
        }

        BigDecimal tolerance = Money.nz(properties.getReconciliation().getVarianceTolerance()).abs();
        List<ZReportResponse.TenderLine> tenders = new ArrayList<>();
        BigDecimal totalReceived = Money.ZERO;
        BigDecimal totalRefunded = Money.ZERO;
        BigDecimal totalExpected = Money.ZERO;
        BigDecimal totalDeclared = reconciliation != null ? Money.ZERO : null;
        BigDecimal expectedCash = Money.ZERO;

        for (TenderTotals totals : tenderTotals) {
            ReconciliationLineEntity line = declaredByMethod.get(totals.method().getId());
            BigDecimal declared = reconciliation != null ? (line != null ? line.getDeclaredAmount() : Money.ZERO) : null;
            BigDecimal variance = declared != null ? declared.subtract(totals.expected()) : null;

            tenders.add(ZReportResponse.TenderLine.builder()
                    .paymentMethodId(totals.method().getId())
                    .name(totals.method().getName())
                    .code(totals.method().getCode())
                    .type(totals.method().getType() != null ? totals.method().getType().name() : null)
                    .transactionCount(totals.count())
                    .received(totals.received())
                    .refunded(totals.refunded())
                    .expected(totals.expected())
                    .declared(declared)
                    .variance(variance)
                    .flagged(variance != null && variance.abs().compareTo(tolerance) > 0)
                    .build());

            totalReceived = totalReceived.add(totals.received());
            totalRefunded = totalRefunded.add(totals.refunded());
            totalExpected = totalExpected.add(totals.expected());
            if (totalDeclared != null) {
                totalDeclared = totalDeclared.add(declared);
            }
            if (totals.method().getType() == PaymentMethodType.CASH) {
                expectedCash = expectedCash.add(totals.expected());
            }
        }

        // ---- Sales ----
        List<SaleEntity> sales = saleRepository.findSoldBetween(start, end);
        long count = 0;
        long voided = 0;
        long refunded = 0;
        long offline = 0;
        long review = 0;
        BigDecimal subtotal = Money.ZERO;
        BigDecimal discounts = Money.ZERO;
        BigDecimal vat = Money.ZERO;
        BigDecimal gross = Money.ZERO;
        BigDecimal outstanding = Money.ZERO;
        Map<String, BigDecimal[]> vatByType = new LinkedHashMap<>();
        long zraSigned = 0;
        long zraPending = 0;
        long zraFailed = 0;
        long zraNotReported = 0;

        for (SaleEntity sale : sales) {
            if (sale.getSaleStatus() == SaleStatus.VOIDED) {
                voided++;
                continue;
            }
            count++;
            if (sale.getSaleStatus() == SaleStatus.REFUNDED) {
                refunded++;
            }
            if (sale.getSource() == SaleSource.OFFLINE_SYNC) {
                offline++;
            }
            if (Boolean.TRUE.equals(sale.getNeedsReview())) {
                review++;
            }

            subtotal = subtotal.add(Money.nz(sale.getSubtotal()));
            discounts = discounts.add(Money.nz(sale.getDiscountAmount()));
            vat = vat.add(Money.nz(sale.getVatAmount()));
            gross = gross.add(Money.nz(sale.getTotalAmount()));

            if (sale.getSaleStatus() == SaleStatus.ACTIVE) {
                BigDecimal paid = sale.getPayments().stream()
                        .filter(p -> p.getPaymentStatus() != PaymentEntity.PaymentStatus.FAILED)
                        .map(PaymentEntity::getAmount)
                        .reduce(Money.ZERO, BigDecimal::add);
                outstanding = outstanding.add(Money.nz(sale.getTotalAmount()).subtract(paid).max(Money.ZERO));
            }

            for (SaleItemEntity item : sale.getItems()) {
                discounts = discounts.add(Money.nz(item.getDiscountAmount()));
                String key = item.getZraTaxType() != null ? item.getZraTaxType().name()
                        : "RATE_" + Money.nz(item.getVatRate()).stripTrailingZeros().toPlainString();
                BigDecimal[] sums = vatByType.computeIfAbsent(key, k -> new BigDecimal[]{Money.ZERO, Money.ZERO, Money.nz(item.getVatRate())});
                sums[0] = sums[0].add(Money.nz(item.getTaxableAmount()));
                sums[1] = sums[1].add(Money.nz(item.getVatAmount()));
            }

            var submission = zraSubmissionRepository
                    .findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(sale.getId(), SubmissionType.SALE_INVOICE);
            if (submission.isEmpty()) {
                zraNotReported++;
            } else if (submission.get().getSubmissionStatus() == SubmissionStatus.SUCCESS) {
                zraSigned++;
            } else if (submission.get().getSubmissionStatus() == SubmissionStatus.FAILED) {
                zraFailed++;
            } else {
                zraPending++;
            }
        }

        List<ZReportResponse.VatLine> vatLines = new ArrayList<>();
        vatByType.forEach((key, sums) -> {
            ZraTaxType type = key.startsWith("RATE_") ? null : ZraTaxType.valueOf(key);
            vatLines.add(ZReportResponse.VatLine.builder()
                    .taxType(type != null ? type.getCode() : null)
                    .label(type != null ? type.getLabel() : "Unmapped (" + key.substring(5) + "%)")
                    .rate(type != null ? type.getRate() : sums[2])
                    .taxableAmount(sums[0])
                    .vatAmount(sums[1])
                    .build());
        });

        // ---- Refunds made today ----
        List<SaleRefundEntity> refunds = saleRefundRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(start, end);

        BigDecimal varianceTotal = totalDeclared != null ? totalDeclared.subtract(totalExpected) : null;

        return ZReportResponse.builder()
                .businessDate(businessDate)
                .generatedAt(LocalDateTime.now(clock))
                .businessName(merchant.getBusinessName())
                .tpin(merchant.getTpin())
                .accountId(merchant.getAccountId())
                .sales(ZReportResponse.SalesSection.builder()
                        .count(count)
                        .voidedCount(voided)
                        .refundedCount(refunded)
                        .offlineCount(offline)
                        .needsReviewCount(review)
                        .subtotal(subtotal)
                        .discounts(discounts)
                        .vat(vat)
                        .grossSales(gross)
                        .outstanding(outstanding)
                        .build())
                .vat(vatLines)
                .refunds(ZReportResponse.RefundSection.builder()
                        .count(refunds.size())
                        .total(refunds.stream().map(SaleRefundEntity::getTotalAmount).reduce(Money.ZERO, BigDecimal::add))
                        .vat(refunds.stream().map(SaleRefundEntity::getVatAmount).reduce(Money.ZERO, BigDecimal::add))
                        .build())
                .tenders(tenders)
                .totals(ZReportResponse.Totals.builder()
                        .received(totalReceived)
                        .refunded(totalRefunded)
                        .expected(totalExpected)
                        .declared(totalDeclared)
                        .variance(varianceTotal)
                        .build())
                .openingFloat(openingFloat)
                .expectedCashInDrawer(expectedCash)
                .reconciliationStatus(reconciliation != null ? reconciliation.getStatus() : "NOT_RECONCILED")
                .reconciledBy(reconciliation != null ? reconciliation.getSubmittedBy() : null)
                .reconciledAt(reconciliation != null ? reconciliation.getSubmittedAt() : null)
                .notes(reconciliation != null ? reconciliation.getNotes() : null)
                .zra(ZReportResponse.ZraSection.builder()
                        .signed(zraSigned)
                        .pending(zraPending)
                        .failed(zraFailed)
                        .notReported(zraNotReported)
                        .build())
                .topProducts(reportService.getTopProducts(businessDate, businessDate, 5))
                .build();
    }

    /**
     * Money per tender for the period: payments received minus refunds paid out; the opening
     * float is added to the main cash tender.
     */
    List<TenderTotals> computeTenders(LocalDateTime start, LocalDateTime end, BigDecimal openingFloat) {

        Map<Long, PaymentMethodEntity> methods = new LinkedHashMap<>();
        Map<Long, long[]> counts = new HashMap<>();
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, BigDecimal> refunded = new HashMap<>();

        paymentMethodRepository.findAll().stream()
                .sorted(Comparator.comparing(PaymentMethodEntity::getId))
                .filter(method -> Boolean.TRUE.equals(method.getIsActive()))
                .forEach(method -> methods.put(method.getId(), method));

        for (PaymentEntity payment : paymentRepository.findReceivedBetween(start, end)) {
            PaymentMethodEntity method = payment.getPaymentMethod();
            methods.putIfAbsent(method.getId(), method);
            counts.computeIfAbsent(method.getId(), id -> new long[1])[0]++;
            received.merge(method.getId(), payment.getAmount(), BigDecimal::add);
        }

        for (SaleRefundPaymentEntity payout : saleRefundRepository.findRefundPaymentsBetween(start, end)) {
            PaymentMethodEntity method = payout.getPaymentMethod();
            methods.putIfAbsent(method.getId(), method);
            refunded.merge(method.getId(), payout.getAmount(), BigDecimal::add);
        }

        Long floatMethodId = methods.values().stream()
                .filter(method -> "CASH".equalsIgnoreCase(method.getCode()))
                .map(PaymentMethodEntity::getId)
                .findFirst()
                .orElseGet(() -> methods.values().stream()
                        .filter(method -> method.getType() == PaymentMethodType.CASH)
                        .map(PaymentMethodEntity::getId)
                        .findFirst()
                        .orElse(null));

        List<TenderTotals> result = new ArrayList<>();
        for (PaymentMethodEntity method : methods.values()) {
            BigDecimal in = Money.of(received.getOrDefault(method.getId(), Money.ZERO));
            BigDecimal out = Money.of(refunded.getOrDefault(method.getId(), Money.ZERO));
            BigDecimal expected = in.subtract(out);
            if (method.getId().equals(floatMethodId)) {
                expected = expected.add(Money.nz(openingFloat));
            }
            result.add(new TenderTotals(method, counts.getOrDefault(method.getId(), new long[1])[0], in, out, Money.of(expected)));
        }
        return result;
    }

    // ---- Exports ------------------------------------------------------------------------

    public String toCsv(ZReportResponse report) {

        StringBuilder csv = new StringBuilder();
        row(csv, "Z-Report", report.getBusinessDate());
        row(csv, "Business", report.getBusinessName());
        row(csv, "TPIN", report.getTpin());
        row(csv, "Account ID", report.getAccountId());
        row(csv, "Generated at", report.getGeneratedAt());
        csv.append('\n');

        row(csv, "Sales", "Value");
        row(csv, "Sales count", report.getSales().getCount());
        row(csv, "Voided sales", report.getSales().getVoidedCount());
        row(csv, "Refunded sales", report.getSales().getRefundedCount());
        row(csv, "Offline (synced) sales", report.getSales().getOfflineCount());
        row(csv, "Subtotal", report.getSales().getSubtotal());
        row(csv, "Discounts", report.getSales().getDiscounts());
        row(csv, "VAT", report.getSales().getVat());
        row(csv, "Gross sales", report.getSales().getGrossSales());
        row(csv, "Outstanding", report.getSales().getOutstanding());
        row(csv, "Refunds count", report.getRefunds().getCount());
        row(csv, "Refunds total", report.getRefunds().getTotal());
        csv.append('\n');

        row(csv, "Tax type", "Rate", "Taxable", "VAT");
        report.getVat().forEach(line -> row(csv, line.getTaxType() != null ? line.getTaxType() : line.getLabel(),
                line.getRate(), line.getTaxableAmount(), line.getVatAmount()));
        csv.append('\n');

        row(csv, "Tender", "Code", "Transactions", "Received", "Refunded", "Expected", "Declared", "Variance", "Flagged");
        report.getTenders().forEach(t -> row(csv, t.getName(), t.getCode(), t.getTransactionCount(), t.getReceived(),
                t.getRefunded(), t.getExpected(), t.getDeclared(), t.getVariance(), t.isFlagged() ? "YES" : ""));
        row(csv, "TOTAL", "", "", report.getTotals().getReceived(), report.getTotals().getRefunded(),
                report.getTotals().getExpected(), report.getTotals().getDeclared(), report.getTotals().getVariance(), "");
        csv.append('\n');

        row(csv, "Opening float", report.getOpeningFloat());
        row(csv, "Expected cash in drawer", report.getExpectedCashInDrawer());
        row(csv, "Reconciliation status", report.getReconciliationStatus());
        row(csv, "Reconciled by", report.getReconciledBy());
        row(csv, "ZRA signed", report.getZra().getSigned());
        row(csv, "ZRA pending", report.getZra().getPending());
        row(csv, "ZRA failed", report.getZra().getFailed());
        return csv.toString();
    }

    public byte[] toPdf(ZReportResponse report) {
        return SimplePdfWriter.renderA4("Z-Report " + report.getBusinessDate(), toLines(report));
    }

    public List<String> toLines(ZReportResponse report) {

        List<String> lines = new ArrayList<>();
        String rule = "-".repeat(78);
        lines.add("Z-REPORT  " + report.getBusinessDate()
                + (report.getBusinessName() != null ? "  " + report.getBusinessName() : ""));
        lines.add("TPIN: " + nvl(report.getTpin()) + "   Account: " + report.getAccountId()
                + "   Generated: " + report.getGeneratedAt().withNano(0));
        lines.add(rule);
        lines.add(String.format("Sales: %d   Voided: %d   Refunded: %d   Offline: %d   Needs review: %d",
                report.getSales().getCount(), report.getSales().getVoidedCount(), report.getSales().getRefundedCount(),
                report.getSales().getOfflineCount(), report.getSales().getNeedsReviewCount()));
        lines.add(String.format("Subtotal %s   Discounts %s   VAT %s   Gross %s   Outstanding %s",
                Money.format(report.getSales().getSubtotal()), Money.format(report.getSales().getDiscounts()),
                Money.format(report.getSales().getVat()), Money.format(report.getSales().getGrossSales()),
                Money.format(report.getSales().getOutstanding())));
        lines.add(String.format("Refunds: %d totalling %s (VAT %s)", report.getRefunds().getCount(),
                Money.format(report.getRefunds().getTotal()), Money.format(report.getRefunds().getVat())));
        lines.add(rule);
        lines.add(String.format("%-28s %8s %14s %14s", "VAT category", "Rate", "Taxable", "VAT"));
        report.getVat().forEach(v -> lines.add(String.format("%-28s %8s %14s %14s",
                truncate((v.getTaxType() != null ? v.getTaxType() + " " : "") + v.getLabel(), 28),
                v.getRate().stripTrailingZeros().toPlainString() + "%",
                Money.format(v.getTaxableAmount()), Money.format(v.getVatAmount()))));
        lines.add(rule);
        lines.add(String.format("%-20s %5s %11s %10s %11s %11s %10s", "Tender", "Txns", "Received", "Refunded",
                "Expected", "Declared", "Variance"));
        report.getTenders().forEach(t -> lines.add(String.format("%-20s %5d %11s %10s %11s %11s %10s%s",
                truncate(t.getName(), 20), t.getTransactionCount(), Money.format(t.getReceived()),
                Money.format(t.getRefunded()), Money.format(t.getExpected()),
                t.getDeclared() != null ? Money.format(t.getDeclared()) : "-",
                t.getVariance() != null ? Money.format(t.getVariance()) : "-",
                t.isFlagged() ? " !" : "")));
        lines.add(String.format("%-20s %5s %11s %10s %11s %11s %10s", "TOTAL", "",
                Money.format(report.getTotals().getReceived()), Money.format(report.getTotals().getRefunded()),
                Money.format(report.getTotals().getExpected()),
                report.getTotals().getDeclared() != null ? Money.format(report.getTotals().getDeclared()) : "-",
                report.getTotals().getVariance() != null ? Money.format(report.getTotals().getVariance()) : "-"));
        lines.add(rule);
        lines.add("Opening float: " + Money.format(report.getOpeningFloat())
                + "   Expected cash in drawer: " + Money.format(report.getExpectedCashInDrawer()));
        lines.add("Reconciliation: " + report.getReconciliationStatus()
                + (report.getReconciledBy() != null ? " by " + report.getReconciledBy() + " at " + report.getReconciledAt().withNano(0) : ""));
        if (report.getNotes() != null) {
            lines.add("Notes: " + report.getNotes());
        }
        lines.add(String.format("ZRA: %d signed, %d pending, %d failed, %d not reported",
                report.getZra().getSigned(), report.getZra().getPending(), report.getZra().getFailed(),
                report.getZra().getNotReported()));
        return lines;
    }

    /** Short summary formatted for WhatsApp (bold with *asterisks*). */
    public String toWhatsAppText(ZReportResponse report) {

        StringBuilder text = new StringBuilder();
        text.append("*Z-Report ").append(report.getBusinessDate()).append("*");
        if (report.getBusinessName() != null) {
            text.append(" - ").append(report.getBusinessName());
        }
        text.append('\n');
        text.append("Sales: ").append(report.getSales().getCount())
                .append(" | K").append(Money.format(report.getSales().getGrossSales()))
                .append(" | VAT K").append(Money.format(report.getSales().getVat())).append('\n');
        if (report.getRefunds().getCount() > 0) {
            text.append("Refunds: ").append(report.getRefunds().getCount())
                    .append(" | K").append(Money.format(report.getRefunds().getTotal())).append('\n');
        }
        text.append('\n');

        for (ZReportResponse.TenderLine tender : report.getTenders()) {
            if (tender.getTransactionCount() == 0 && tender.getRefunded().signum() == 0
                    && tender.getExpected().signum() == 0 && tender.getDeclared() == null) {
                continue;
            }
            text.append(tender.getName()).append(": expected K").append(Money.format(tender.getExpected()));
            if (tender.getDeclared() != null) {
                text.append(" | counted K").append(Money.format(tender.getDeclared()))
                        .append(" | ").append(signed(tender.getVariance()));
                if (tender.isFlagged()) {
                    text.append(" (!)");
                }
            }
            text.append('\n');
        }

        text.append('\n').append("Status: ").append(report.getReconciliationStatus());
        if (report.getTotals().getVariance() != null && report.getTotals().getVariance().signum() != 0) {
            text.append(" (").append(signed(report.getTotals().getVariance())).append(")");
        }
        text.append('\n');
        text.append("ZRA: ").append(report.getZra().getSigned()).append(" signed, ")
                .append(report.getZra().getPending()).append(" pending, ")
                .append(report.getZra().getFailed()).append(" failed");
        return text.toString();
    }

    private static void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now().plusDays(1))) {
            throw new BadRequestException("Report date cannot be in the future");
        }
    }

    private static void row(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            Object value = values[i] instanceof BigDecimal decimal ? Money.format(decimal) : values[i];
            csv.append(CsvParser.escape(value));
        }
        csv.append('\n');
    }

    private static String signed(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return (value.signum() > 0 ? "+" : "") + Money.format(value);
    }

    private static String truncate(String value, int max) {
        return value == null ? "" : value.length() <= max ? value : value.substring(0, max);
    }

    private static String nvl(String value) {
        return value != null ? value : "-";
    }
}
