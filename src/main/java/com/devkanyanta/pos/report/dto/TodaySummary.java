package com.devkanyanta.pos.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/** Today's total – available on every plan, including MICRO. */
@Getter
@Builder
public class TodaySummary {

    private LocalDate date;

    private long salesCount;

    private BigDecimal totalSales;

    /** Money received today per tender (name -> amount). */
    private Map<String, BigDecimal> receivedByMethod;
}
