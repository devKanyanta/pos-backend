package com.devkanyanta.pos.receipt;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final ReceiptPrintService receiptPrintService;

    private final com.devkanyanta.pos.whatsapp.WhatsAppService whatsAppService;

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

    /** Plain-text receipt for thermal printers; width in characters (32-64, default 42). */
    @GetMapping(value = "/{id}/print", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_VIEW + "')")
    public String printText(
            @PathVariable Long id,
            @RequestParam(value = "width", defaultValue = "42") int width
    ) {
        return receiptPrintService.renderText(id, width);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_VIEW + "')")
    public ResponseEntity<byte[]> printPdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(receiptPrintService.renderPdf(id));
    }

    /**
     * WhatsApp receipt for the customer: returns the text and a click-to-chat link; with
     * {@code send=true} it is also sent through the WhatsApp Business provider.
     */
    @PostMapping("/{id}/share/whatsapp")
    @PreAuthorize("hasAuthority('" + PermissionConstants.RECEIPT_VIEW + "')")
    public com.devkanyanta.pos.support.dto.ShareResponse shareViaWhatsApp(
            @PathVariable Long id,
            @RequestParam(value = "send", defaultValue = "false") boolean send,
            @RequestBody com.devkanyanta.pos.support.dto.SendWhatsAppRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        if (send && authentication.getAuthorities().stream()
                .noneMatch(a -> PermissionConstants.WHATSAPP_SEND.equals(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("WHATSAPP_SEND permission required");
        }

        String text = "```\n" + receiptPrintService.renderText(id, 32) + "```";

        return com.devkanyanta.pos.support.dto.ShareResponse.builder()
                .text(text)
                .shareUrl(whatsAppService.clickToChatLink(request.getPhone(), text))
                .sent(send ? whatsAppService.send(request.getPhone(), text, "RECEIPT", id, authentication.getName()) : null)
                .build();
    }
}
