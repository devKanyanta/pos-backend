package com.devkanyanta.pos.payment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePaymentMethodRequest {

    private String name;

    private String code;

    private Boolean isActive;

    /** CASH, MOBILE_MONEY, CARD, BANK_TRANSFER or OTHER. */
    private String type;

    /** Whether a transaction reference must be captured (defaults to true for mobile money). */
    private Boolean requiresReference;

    /** ZRA payment type code (pmtTyCd). */
    private String zraPaymentTypeCode;
}
