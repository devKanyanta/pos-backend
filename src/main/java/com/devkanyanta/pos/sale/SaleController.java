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
import com.devkanyanta.pos.sale.dto.VoidRequest;

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
            @PathVariable Long id,
            @RequestBody(required = false) VoidRequest request,
            Authentication authentication
    ) {
        return saleService.voidSale(id, username(authentication), request != null ? request.getReason() : null);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_REFUND + "')")
    @ResponseStatus(HttpStatus.OK)
    public SaleResponse refundSale(
            @PathVariable Long id,
            @RequestBody(required = false) RefundRequest request,
            Authentication authentication
    ) {
        return saleService.refundSale(id, request != null ? request : new RefundRequest(), username(authentication));
    }

    /** Offline-synced sales flagged with conflicts (negative stock, price mismatch, ...). */
    @GetMapping("/needs-review")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DEVICE_MANAGE + "')")
    public List<SaleResponse> getSalesNeedingReview() {
        return saleService.getSalesNeedingReview();
    }

    /** Marks a flagged sale as reviewed by a manager. */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DEVICE_MANAGE + "')")
    public SaleResponse markReviewed(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return saleService.markReviewed(id, username(authentication));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public List<SaleResponse> getSalesByDateRange(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to
    ) {
        return saleService.getSalesByDateRange(from, to);
    }

    private static String username(Authentication authentication) {
        return authentication != null ? authentication.getName() : "system";
    }
}
