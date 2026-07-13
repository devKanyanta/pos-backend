package com.devkanyanta.pos.sale.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundRequest {

    private List<Long> saleItemIds;

    private String reason;
}
