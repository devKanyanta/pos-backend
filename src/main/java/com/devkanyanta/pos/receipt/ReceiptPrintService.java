package com.devkanyanta.pos.receipt;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.merchant.MerchantProfileEntity;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.pdf.SimplePdfWriter;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.zra.ZraDeviceRepository;
import com.devkanyanta.pos.zra.ZraSubmissionEntity;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;
import com.devkanyanta.pos.zra.ZraTaxType;

import lombok.RequiredArgsConstructor;

/**
 * Renders receipts for thermal printers (plain text), PDF and messaging. Includes the ZRA Smart
 * Invoice verification block (SDC id, MRC, receipt number, internal data, signature, QR link)
 * once the VSDC has signed the sale.
 */
@Service
@RequiredArgsConstructor
public class ReceiptPrintService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReceiptRepository receiptRepository;

    private final MerchantProfileService merchantProfileService;

    private final ZraSubmissionRepository zraSubmissionRepository;

    private final ZraDeviceRepository zraDeviceRepository;

    @Transactional(readOnly = true)
    public String renderText(Long receiptId, int width) {
        return String.join("\n", renderLines(receiptId, width)) + "\n";
    }

    @Transactional(readOnly = true)
    public byte[] renderPdf(Long receiptId) {

        List<String> lines = renderLines(receiptId, 42);
        ReceiptEntity receipt = receiptRepository.findById(receiptId).orElseThrow();
        int height = Math.max(300, lines.size() * 12 + 80);
        return SimplePdfWriter.render("Receipt " + receipt.getReceiptNumber(), lines, 300, height);
    }

    @Transactional(readOnly = true)
    public List<String> renderLines(Long receiptId, int requestedWidth) {

        int width = Math.max(32, Math.min(64, requestedWidth));

        ReceiptEntity receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));
        SaleEntity sale = receipt.getSale();
        MerchantProfileEntity merchant = merchantProfileService.getOrCreate();

        List<String> out = new ArrayList<>();
        String rule = "-".repeat(width);

        String businessName = merchant.getBusinessName() != null ? merchant.getBusinessName()
                : zraDeviceRepository.findFirstByActiveTrueOrderByIdDesc().map(d -> d.getTaxpayerName()).orElse("RECEIPT");
        out.add(center(businessName.toUpperCase(), width));
        if (merchant.getAddress() != null) {
            wrap(merchant.getAddress(), width).forEach(line -> out.add(center(line, width)));
        }
        if (merchant.getPhone() != null) {
            out.add(center("Tel: " + merchant.getPhone(), width));
        }
        String tpin = merchant.getTpin() != null ? merchant.getTpin()
                : zraDeviceRepository.findFirstByActiveTrueOrderByIdDesc().map(d -> d.getTpin()).orElse(null);
        if (tpin != null) {
            out.add(center("TPIN: " + tpin, width));
        }
        out.add(rule);

        if (receipt.getPrintedCount() != null && receipt.getPrintedCount() > 1) {
            out.add(center("*** COPY " + receipt.getPrintedCount() + " ***", width));
        }
        out.add("Receipt: " + receipt.getReceiptNumber());
        out.add("Sale:    " + sale.getSaleNumber());
        out.add("Date:    " + DATE_TIME.format(sale.getSoldAt() != null ? sale.getSoldAt() : sale.getCreatedAt()));
        out.add("Cashier: " + sale.getCreatedBy());
        if (sale.getCustomer() != null) {
            String name = ((sale.getCustomer().getFirstName() != null ? sale.getCustomer().getFirstName() + " " : "")
                    + (sale.getCustomer().getLastName() != null ? sale.getCustomer().getLastName() : "")).trim();
            out.add("Customer: " + (name.isEmpty() ? sale.getCustomer().getPhone() : name));
            if (sale.getCustomer().getTpin() != null) {
                out.add("Customer TPIN: " + sale.getCustomer().getTpin());
            }
        }
        out.add(rule);

        Map<ZraTaxType, BigDecimal[]> taxSummary = new EnumMap<>(ZraTaxType.class);

        for (SaleItemEntity item : sale.getItems()) {
            ZraTaxType type = item.getZraTaxType();
            wrap(item.getProductName(), width).forEach(out::add);
            out.add(columns("  " + item.getQuantity() + " x " + Money.format(item.getUnitPrice()),
                    Money.format(item.getGrossAmount() != null ? item.getGrossAmount() : item.getLineTotal())
                            + (type != null ? " " + type.getCode() : ""), width));
            if (Money.nz(item.getDiscountAmount()).signum() > 0) {
                out.add(columns("  Discount", "-" + Money.format(item.getDiscountAmount()), width));
            }
            if (item.getRefundedQuantity() != null && item.getRefundedQuantity() > 0) {
                out.add(columns("  Refunded qty", String.valueOf(item.getRefundedQuantity()), width));
            }
            if (type != null) {
                BigDecimal[] sums = taxSummary.computeIfAbsent(type, t -> new BigDecimal[]{Money.ZERO, Money.ZERO});
                sums[0] = sums[0].add(Money.nz(item.getTaxableAmount()));
                sums[1] = sums[1].add(Money.nz(item.getVatAmount()));
            }
        }

        out.add(rule);
        out.add(columns("Subtotal", Money.format(sale.getSubtotal()), width));
        if (Money.nz(sale.getDiscountAmount()).signum() > 0) {
            out.add(columns("Discount", "-" + Money.format(sale.getDiscountAmount()), width));
        }
        taxSummary.forEach((type, sums) -> out.add(columns(
                "VAT " + type.getCode() + " " + type.getRate().stripTrailingZeros().toPlainString() + "% on " + Money.format(sums[0]),
                Money.format(sums[1]), width)));
        out.add(columns("Total VAT", Money.format(sale.getVatAmount()), width));
        out.add(columns("TOTAL ZMW", Money.format(sale.getTotalAmount()), width));
        out.add(rule);

        BigDecimal change = Money.ZERO;
        for (PaymentEntity payment : sale.getPayments()) {
            if (payment.getPaymentStatus() == PaymentEntity.PaymentStatus.FAILED) {
                continue;
            }
            BigDecimal shown = payment.getAmountTendered() != null ? payment.getAmountTendered() : payment.getAmount();
            out.add(columns(payment.getPaymentMethod().getName(), Money.format(shown), width));
            if (payment.getTransactionReference() != null) {
                out.add("  Ref: " + payment.getTransactionReference());
            }
            change = change.add(Money.nz(payment.getChangeAmount()));
        }
        if (change.signum() > 0) {
            out.add(columns("Change", Money.format(change), width));
        }
        if (Money.nz(sale.getRefundedAmount()).signum() > 0) {
            out.add(columns("Refunded", "-" + Money.format(sale.getRefundedAmount()), width));
        }
        out.add(rule);

        ZraSubmissionEntity submission = zraSubmissionRepository
                .findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(sale.getId(), SubmissionType.SALE_INVOICE)
                .orElse(null);

        if (receipt.isZraSigned()) {
            out.add(center("ZRA SMART INVOICE", width));
            if (receipt.getZraSdcId() != null) {
                out.add("SDC ID: " + receipt.getZraSdcId());
            }
            if (receipt.getZraMrcNo() != null) {
                out.add("MRC No: " + receipt.getZraMrcNo());
            }
            if (receipt.getZraReceiptNo() != null) {
                out.add("Receipt No: " + receipt.getZraReceiptNo());
            }
            if (receipt.getZraInvoiceNo() != null) {
                out.add("Invoice No: " + receipt.getZraInvoiceNo());
            }
            if (receipt.getZraReceiptDate() != null) {
                out.add("Signed: " + receipt.getZraReceiptDate());
            }
            if (receipt.getZraInternalData() != null) {
                wrap("Internal Data: " + receipt.getZraInternalData(), width).forEach(out::add);
            }
            wrap("Verification: " + receipt.getZraReceiptSignature(), width).forEach(out::add);
            if (receipt.getZraQrCodeUrl() != null) {
                wrap("Verify: " + receipt.getZraQrCodeUrl(), width).forEach(out::add);
            }
            out.add(rule);
        } else if (submission != null) {
            out.add(center("ZRA: awaiting signature (" + submission.getSubmissionStatus().name().toLowerCase() + ")", width));
            out.add(rule);
        }

        if (merchant.getReceiptFooter() != null) {
            wrap(merchant.getReceiptFooter(), width).forEach(line -> out.add(center(line, width)));
        } else {
            out.add(center("Thank you!", width));
        }

        return out;
    }

    private static String columns(String left, String right, int width) {

        int space = width - right.length();
        if (left.length() >= space) {
            left = left.substring(0, Math.max(0, space - 1));
        }
        return left + " ".repeat(Math.max(1, width - left.length() - right.length())) + right;
    }

    private static String center(String text, int width) {

        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int pad = (width - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    private static List<String> wrap(String text, int width) {

        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        String remaining = text;
        while (remaining.length() > width) {
            int cut = remaining.lastIndexOf(' ', width);
            if (cut <= 0) {
                cut = width;
            }
            lines.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }
        return lines;
    }
}
