package com.devkanyanta.pos.whatsapp;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsAppSendResult {

    private Long messageId;

    private String to;

    private String provider;

    /** SENT or FAILED. */
    private String status;

    private String providerMessageId;

    private String error;

    /** Click-to-chat link with the same text, usable from the device as a fallback. */
    private String shareUrl;

    private LocalDateTime sentAt;
}
