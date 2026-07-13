package com.devkanyanta.pos.sale;

import java.time.LocalDate;
import java.util.List;

import com.devkanyanta.pos.sale.dto.CreateSaleRequest;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.sale.dto.SaleResponse;

public interface SaleService {

    SaleResponse createSale(CreateSaleRequest request, String username);

    List<SaleResponse> getAllSales();

    SaleResponse getSaleById(Long id);

    SaleResponse getSaleByNumber(String saleNumber);

    SaleResponse voidSale(Long id);

    SaleResponse refundSale(Long id, RefundRequest request);

    List<SaleResponse> getSalesByDateRange(LocalDate from, LocalDate to);
}
