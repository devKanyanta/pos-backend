package com.devkanyanta.pos.support.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendWhatsAppRequest {

    /** Recipient phone, local (0971234567) or international (260971234567 / +260971234567). */
    private String phone;
}
