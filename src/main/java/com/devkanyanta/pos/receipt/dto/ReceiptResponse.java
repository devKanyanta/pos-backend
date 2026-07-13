package com.devkanyanta.pos.receipt.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReceiptResponse {

    private Long id;

    private Long saleId;

    private String saleNumber;

    private String receiptNumber;

    private Integer printedCount;

    private LocalDateTime createdAt;
}
