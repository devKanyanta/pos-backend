package com.devkanyanta.pos.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SaleResponse {

    private Long id;

    private String saleNumber;

    private Long customerId;

    private String customerName;

    private String customerPhone;

    private BigDecimal subtotal;

    private BigDecimal discountAmount;

    private BigDecimal vatAmount;

    private BigDecimal totalAmount;

    private String paymentStatus;

    private String saleStatus;

    private String notes;

    private String createdBy;

    private LocalDateTime createdAt;

    private List<SaleItemResponse> items;
}
