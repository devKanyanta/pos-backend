package com.devkanyanta.pos.payment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.payment.dto.CreatePaymentMethodRequest;
import com.devkanyanta.pos.payment.dto.PaymentMethodResponse;
import com.devkanyanta.pos.payment.dto.UpdatePaymentMethodRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.PAYMENT_METHOD_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse createPaymentMethod(
            @RequestBody CreatePaymentMethodRequest request
    ) {
        return paymentMethodService.createPaymentMethod(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.PAYMENT_METHOD_VIEW + "')")
    public List<PaymentMethodResponse> getAllPaymentMethods() {
        return paymentMethodService.getAllPaymentMethods();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PAYMENT_METHOD_VIEW + "')")
    public PaymentMethodResponse getPaymentMethodById(
            @PathVariable Long id
    ) {
        return paymentMethodService.getPaymentMethodById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PAYMENT_METHOD_UPDATE + "')")
    public PaymentMethodResponse updatePaymentMethod(
            @PathVariable Long id,
            @RequestBody UpdatePaymentMethodRequest request
    ) {
        return paymentMethodService.updatePaymentMethod(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PAYMENT_METHOD_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePaymentMethod(
            @PathVariable Long id
    ) {
        paymentMethodService.deletePaymentMethod(id);
    }
}
