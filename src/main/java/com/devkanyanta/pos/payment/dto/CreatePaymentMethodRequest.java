package com.devkanyanta.pos.payment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentMethodRequest {

    private String name;

    private String code;
}
