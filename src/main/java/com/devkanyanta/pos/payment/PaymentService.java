package com.devkanyanta.pos.payment;

import java.time.LocalDateTime;
import java.util.List;

import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.payment.dto.PaymentResponse;
import com.devkanyanta.pos.sale.SaleEntity;

public interface PaymentService {

    PaymentResponse createPayment(Long saleId, CreatePaymentRequest request);

    List<PaymentResponse> getPaymentsBySaleId(Long saleId);

    /**
     * Applies a tender to a (locked) sale inside the caller's transaction.
     *
     * @param offline offline-sync mode: problems that cannot be undone (the money was already
     *                taken) are recorded as conflicts instead of rejecting the payment
     */
    PaymentOutcome applyPayment(SaleEntity sale, CreatePaymentRequest request, String username,
                                LocalDateTime paidAt, boolean offline);

    PaymentResponse toResponse(PaymentEntity payment);

    record PaymentOutcome(PaymentEntity payment, List<String> conflicts) {
    }
}
