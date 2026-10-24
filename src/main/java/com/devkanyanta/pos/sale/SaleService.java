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

    SaleResponse voidSale(Long id, String username, String reason);

    default SaleResponse voidSale(Long id, String username) {
        return voidSale(id, username, null);
    }

    SaleResponse refundSale(Long id, RefundRequest request, String username);

    List<SaleResponse> getSalesByDateRange(LocalDate from, LocalDate to);

    List<SaleResponse> getSalesNeedingReview();

    SaleResponse markReviewed(Long id, String username);
}
