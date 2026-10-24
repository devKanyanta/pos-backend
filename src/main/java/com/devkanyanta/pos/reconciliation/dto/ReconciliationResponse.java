package com.devkanyanta.pos.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReconciliationResponse {

    private Long id;

    private LocalDate businessDate;

    private BigDecimal openingFloat;

    private BigDecimal expectedTotal;

    private BigDecimal declaredTotal;

    private BigDecimal varianceTotal;

    private String status;

    private String notes;

    private String submittedBy;

    private LocalDateTime submittedAt;

    private List<Line> lines;

    @Getter
    @Builder
    public static class Line {

        private Long paymentMethodId;

        private String paymentMethodName;

        private Integer transactionCount;

        private BigDecimal expectedAmount;

        private BigDecimal declaredAmount;

        private BigDecimal variance;

        private Boolean flagged;
    }
}
