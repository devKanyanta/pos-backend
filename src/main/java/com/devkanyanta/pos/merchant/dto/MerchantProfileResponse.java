package com.devkanyanta.pos.merchant.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MerchantProfileResponse {

    private String accountId;

    private String businessName;

    private String tpin;

    private String phone;

    private String email;

    private String address;

    private String receiptFooter;

    private String plan;

    /** Feature flags derived from the plan, so clients can hide unavailable features. */
    private Map<String, Boolean> features;
}
