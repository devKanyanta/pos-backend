package com.devkanyanta.pos.support.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SupportGuideResponse {

    private Long id;

    private String title;

    private String topic;

    private String description;

    private String mediaUrl;

    private Integer sortOrder;

    private Boolean active;

    /** Opens a WhatsApp chat with support asking for this guide (null if no support number). */
    private String requestViaWhatsAppUrl;

    private LocalDateTime updatedAt;
}
