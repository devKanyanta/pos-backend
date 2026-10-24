package com.devkanyanta.pos.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {

    private Long id;

    private Long saleId;

    private Long paymentMethodId;

    private String paymentMethodName;

    private String paymentMethodCode;

    private String paymentMethodType;

    /** Amount applied to the sale. */
    private BigDecimal amount;

    private BigDecimal amountTendered;

    private BigDecimal changeAmount;

    private BigDecimal refundedAmount;

    private String transactionReference;

    private String payerPhone;

    private String paymentStatus;

    private String recordedBy;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    /** Sale balance after this payment. */
    private BigDecimal saleBalanceDue;

    /** Sale payment status after this payment. */
    private String salePaymentStatus;

    private Long receiptId;

    private String receiptNumber;
}
