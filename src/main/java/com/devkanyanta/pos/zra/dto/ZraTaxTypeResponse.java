package com.devkanyanta.pos.zra.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZraTaxTypeResponse {

    private String code;

    private String label;

    private BigDecimal rate;

    private boolean zeroRated;

    private boolean exempt;
}
