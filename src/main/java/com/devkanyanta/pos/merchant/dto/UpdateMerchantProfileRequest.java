package com.devkanyanta.pos.merchant.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMerchantProfileRequest {

    private String businessName;

    private String tpin;

    private String phone;

    private String email;

    private String address;

    private String receiptFooter;

    /** MICRO or SME. */
    private String plan;
}
