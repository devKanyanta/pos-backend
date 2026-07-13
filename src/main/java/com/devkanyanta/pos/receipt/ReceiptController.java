package com.devkanyanta.pos.receipt;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.receipt.dto.ReceiptResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_VIEW + "')")
    public ReceiptResponse getReceiptById(
            @PathVariable Long id
    ) {
        return receiptService.getReceiptById(id);
    }

    @GetMapping("/sale/{saleId}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_VIEW + "')")
    public ReceiptResponse getReceiptBySaleId(
            @PathVariable Long saleId
    ) {
        return receiptService.getReceiptBySaleId(saleId);
    }

    @PostMapping("/{id}/reprint")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_REPRINT + "')")
    @ResponseStatus(HttpStatus.OK)
    public ReceiptResponse reprintReceipt(
            @PathVariable Long id
    ) {
        return receiptService.reprintReceipt(id);
    }
}
