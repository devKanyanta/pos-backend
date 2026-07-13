package com.devkanyanta.pos.sale;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.sale.dto.CreateSaleRequest;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.sale.dto.SaleResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse createSale(
            @RequestBody CreateSaleRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "system";
        return saleService.createSale(request, username);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public List<SaleResponse> getAllSales() {
        return saleService.getAllSales();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public SaleResponse getSaleById(
            @PathVariable Long id
    ) {
        return saleService.getSaleById(id);
    }

    @GetMapping("/number/{saleNumber}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public SaleResponse getSaleByNumber(
            @PathVariable String saleNumber
    ) {
        return saleService.getSaleByNumber(saleNumber);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VOID + "')")
    @ResponseStatus(HttpStatus.OK)
    public SaleResponse voidSale(
            @PathVariable Long id
    ) {
        return saleService.voidSale(id);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_REFUND + "')")
    @ResponseStatus(HttpStatus.OK)
    public SaleResponse refundSale(
            @PathVariable Long id,
            @RequestBody RefundRequest request
    ) {
        return saleService.refundSale(id, request);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public List<SaleResponse> getSalesByDateRange(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to
    ) {
        return saleService.getSalesByDateRange(from, to);
    }
}
