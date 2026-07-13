package com.devkanyanta.pos.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentMethodResponse {

    private Long id;

    private String name;

    private String code;

    private Boolean isActive;
}
