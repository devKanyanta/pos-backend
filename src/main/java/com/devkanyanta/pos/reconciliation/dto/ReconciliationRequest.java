package com.devkanyanta.pos.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReconciliationRequest {

    /** Defaults to today. */
    private LocalDate businessDate;

    private BigDecimal openingFloat;

    /** Counted / statement amount per tender. Tenders left out are declared as 0. */
    private List<Declared> declared;

    private String notes;

    @Getter
    @Setter
    public static class Declared {

        private Long paymentMethodId;

        private BigDecimal amount;
    }
}
