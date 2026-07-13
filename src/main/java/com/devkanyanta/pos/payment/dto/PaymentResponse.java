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

    private BigDecimal amount;

    private String transactionReference;

    private String paymentStatus;

    private LocalDateTime createdAt;
}
