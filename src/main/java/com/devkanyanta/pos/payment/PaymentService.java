package com.devkanyanta.pos.payment;

import java.util.List;

import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.payment.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse createPayment(Long saleId, CreatePaymentRequest request);

    List<PaymentResponse> getPaymentsBySaleId(Long saleId);
}
