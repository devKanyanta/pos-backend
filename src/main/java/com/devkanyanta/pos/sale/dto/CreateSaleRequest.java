package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSaleRequest {

    private Long customerId;

    private String notes;

    private BigDecimal discountAmount;

    private List<SaleItemRequest> items;
}
