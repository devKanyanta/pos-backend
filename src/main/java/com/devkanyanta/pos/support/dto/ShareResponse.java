package com.devkanyanta.pos.support.dto;

import com.devkanyanta.pos.whatsapp.WhatsAppSendResult;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShareResponse {

    /** Message text that is / would be sent. */
    private String text;

    /** Click-to-chat link (opens WhatsApp on the device with the text pre-filled). */
    private String shareUrl;

    /** Result of sending through the provider, when requested and configured. */
    private WhatsAppSendResult sent;
}
