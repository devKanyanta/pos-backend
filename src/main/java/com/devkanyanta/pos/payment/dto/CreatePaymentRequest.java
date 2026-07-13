package com.devkanyanta.pos.payment.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentRequest {

    private Long paymentMethodId;

    private BigDecimal amount;

    private String transactionReference;
}
