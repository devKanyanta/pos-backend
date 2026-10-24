package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.devkanyanta.pos.payment.dto.PaymentResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SaleResponse {

    private Long id;

    private String saleNumber;

    private Long customerId;

    private String customerName;

    private String customerPhone;

    private BigDecimal subtotal;

    private BigDecimal discountAmount;

    private BigDecimal vatAmount;

    private BigDecimal totalAmount;

    private BigDecimal amountPaid;

    private BigDecimal balanceDue;

    private BigDecimal changeGiven;

    private BigDecimal refundedAmount;

    private String paymentStatus;

    private String saleStatus;

    private String notes;

    private String createdBy;

    private LocalDateTime createdAt;

    /** When the sale happened (device time for offline sales). */
    private LocalDateTime soldAt;

    private String clientSaleId;

    private String deviceId;

    private String clientReceiptNumber;

    /** ONLINE or OFFLINE_SYNC. */
    private String source;

    private Boolean needsReview;

    private List<String> syncConflicts;

    private Long receiptId;

    private String receiptNumber;

    /** ZRA invoice status: PENDING, RETRYING, SUCCESS, FAILED, or null when not reported. */
    private String zraStatus;

    private List<SaleItemResponse> items;

    private List<PaymentResponse> payments;

    private List<RefundResponse> refunds;
}
