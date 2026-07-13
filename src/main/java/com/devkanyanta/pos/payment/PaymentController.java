package com.devkanyanta.pos.payment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.payment.dto.PaymentResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sales/{saleId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @PathVariable Long saleId,
            @RequestBody CreatePaymentRequest request
    ) {
        return paymentService.createPayment(saleId, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SALE_VIEW + "')")
    public List<PaymentResponse> getPayments(
            @PathVariable Long saleId
    ) {
        return paymentService.getPaymentsBySaleId(saleId);
    }
}
