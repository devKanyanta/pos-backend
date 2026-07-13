package com.devkanyanta.pos.zra.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZraSubmissionResponse {

    private Long id;

    private Long saleId;

    private String saleNumber;

    private Long receiptId;

    private String submissionStatus;

    private String submissionType;

    private Integer retryCount;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime createdAt;
}
