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

    private Long refundId;

    private String refundNumber;

    private Long productId;

    private Long invoiceNo;

    private String submissionStatus;

    private String submissionType;

    private Integer retryCount;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    /** Only included on the single-submission endpoint. */
    private String requestPayload;

    private String responsePayload;
}
