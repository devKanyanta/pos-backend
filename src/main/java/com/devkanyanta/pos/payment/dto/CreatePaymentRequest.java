package com.devkanyanta.pos.payment.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentRequest {

    private Long paymentMethodId;

    /**
     * Amount received. For cash this may exceed the amount due; the difference is returned as
     * change. For other tenders it may not exceed the balance due.
     */
    private BigDecimal amount;

    /** Mobile money confirmation code / card approval code. Required for mobile money. */
    private String transactionReference;

    /** Mobile money payer number (optional). */
    private String payerPhone;
}
