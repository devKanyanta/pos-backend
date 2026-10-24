package com.devkanyanta.pos.support.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsAppHelpResponse {

    private String supportNumber;

    private String accountId;

    private String username;

    /** Pre-filled message including the merchant account id. */
    private String message;

    /** https://wa.me/... link for the in-app Help button. */
    private String url;
}
