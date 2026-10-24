package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentService;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.dto.RefundResponse;
import com.devkanyanta.pos.sale.dto.SaleItemResponse;
import com.devkanyanta.pos.sale.dto.SaleResponse;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundRepository;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.zra.ZraSubmissionEntity;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionType;
import com.devkanyanta.pos.zra.ZraSubmissionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SaleMapper {

    private final PaymentService paymentService;

    private final ReceiptRepository receiptRepository;

    private final SaleRefundRepository saleRefundRepository;

    private final ZraSubmissionRepository zraSubmissionRepository;

    public SaleResponse toResponse(SaleEntity sale) {

        List<PaymentEntity> payments = sale.getPayments();

        BigDecimal paid = payments.stream()
                .filter(p -> p.getPaymentStatus() != PaymentEntity.PaymentStatus.FAILED)
                .map(PaymentEntity::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);

        BigDecimal change = payments.stream()
                .map(p -> Money.nz(p.getChangeAmount()))
                .reduce(Money.ZERO, BigDecimal::add);

        ReceiptEntity receipt = sale.getId() != null
                ? receiptRepository.findBySaleId(sale.getId()).orElse(null)
                : null;

        String zraStatus = sale.getId() != null
                ? zraSubmissionRepository.findFirstBySaleIdAndSubmissionTypeOrderByIdDesc(sale.getId(), SubmissionType.SALE_INVOICE)
                        .map(z -> z.getSubmissionStatus().name())
                        .orElse(null)
                : null;

        BigDecimal balance = sale.getSaleStatus() == SaleEntity.SaleStatus.ACTIVE
                ? Money.of(sale.getTotalAmount()).subtract(paid).max(Money.ZERO)
                : Money.ZERO;

        SaleResponse.SaleResponseBuilder builder = SaleResponse.builder()
                .id(sale.getId())
                .saleNumber(sale.getSaleNumber())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .vatAmount(sale.getVatAmount())
                .totalAmount(sale.getTotalAmount())
                .amountPaid(paid)
                .balanceDue(balance)
                .changeGiven(change)
                .refundedAmount(Money.of(sale.getRefundedAmount()))
                .paymentStatus(sale.getPaymentStatus().name())
                .saleStatus(sale.getSaleStatus().name())
                .notes(sale.getNotes())
                .createdBy(sale.getCreatedBy())
                .createdAt(sale.getCreatedAt())
                .soldAt(sale.getSoldAt())
                .clientSaleId(sale.getClientSaleId())
                .deviceId(sale.getDeviceId())
                .clientReceiptNumber(sale.getClientReceiptNumber())
                .source(sale.getSource() != null ? sale.getSource().name() : null)
                .needsReview(Boolean.TRUE.equals(sale.getNeedsReview()))
                .syncConflicts(StringUtils.hasText(sale.getSyncConflicts())
                        ? Arrays.asList(sale.getSyncConflicts().split(","))
                        : List.of())
                .receiptId(receipt != null ? receipt.getId() : null)
                .receiptNumber(receipt != null ? receipt.getReceiptNumber() : null)
                .zraStatus(zraStatus);

        if (sale.getCustomer() != null) {
            builder.customerId(sale.getCustomer().getId())
                    .customerName(
                            ((sale.getCustomer().getFirstName() != null ? sale.getCustomer().getFirstName() + " " : "")
                            + (sale.getCustomer().getLastName() != null ? sale.getCustomer().getLastName() : "")).trim()
                    )
                    .customerPhone(sale.getCustomer().getPhone());
        }

        builder.items(sale.getItems().stream()
                .map(this::toItemResponse)
                .toList());

        builder.payments(payments.stream()
                .map(paymentService::toResponse)
                .toList());

        builder.refunds(sale.getId() == null ? List.of() : saleRefundRepository.findBySaleIdOrderByIdAsc(sale.getId())
                .stream()
                .map(this::toRefundResponse)
                .toList());

        return builder.build();
    }

    public RefundResponse toRefundResponse(SaleRefundEntity refund) {

        ZraSubmissionEntity submission = refund.getId() != null
                ? zraSubmissionRepository.findFirstByRefundIdOrderByIdDesc(refund.getId()).orElse(null)
                : null;

        String zraReceiptNumber = refund.getZraReceiptNo() != null ? refund.getZraReceiptNo().toString() : null;

        return RefundResponse.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .totalAmount(refund.getTotalAmount())
                .vatAmount(refund.getVatAmount())
                .reason(refund.getReason())
                .zraReasonCode(refund.getZraReasonCode())
                .createdBy(refund.getCreatedBy())
                .createdAt(refund.getCreatedAt())
                .zraStatus(submission != null ? submission.getSubmissionStatus().name() : null)
                .zraReceiptNumber(zraReceiptNumber)
                .items(refund.getItems().stream()
                        .map(item -> RefundResponse.Line.builder()
                                .saleItemId(item.getSaleItem().getId())
                                .productName(item.getSaleItem().getProductName())
                                .quantity(item.getQuantity())
                                .totalAmount(item.getTotalAmount())
                                .vatAmount(item.getVatAmount())
                                .build())
                        .toList())
                .payouts(refund.getPayments().stream()
                        .map(payout -> RefundResponse.Tender.builder()
                                .paymentId(payout.getPayment() != null ? payout.getPayment().getId() : null)
                                .paymentMethodId(payout.getPaymentMethod().getId())
                                .paymentMethodName(payout.getPaymentMethod().getName())
                                .amount(payout.getAmount())
                                .build())
                        .toList())
                .build();
    }

    public SaleItemResponse toItemResponse(SaleItemEntity item) {

        return SaleItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .grossAmount(item.getGrossAmount())
                .lineTotal(item.getLineTotal())
                .saleDiscountShare(item.getSaleDiscountShare())
                .taxableAmount(item.getTaxableAmount())
                .vatRate(item.getVatRate())
                .vatAmount(item.getVatAmount())
                .discountAmount(item.getDiscountAmount())
                .totalAmount(item.getTotalInclVat())
                .zraTaxType(item.getZraTaxType() != null ? item.getZraTaxType().getCode() : null)
                .itemStatus(item.getItemStatus().name())
                .refundedQuantity(item.getRefundedQuantity())
                .refundedAmount(item.getRefundedAmount())
                .build();
    }
}
