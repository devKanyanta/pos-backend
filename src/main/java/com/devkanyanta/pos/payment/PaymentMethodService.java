package com.devkanyanta.pos.payment;

import java.util.List;

import com.devkanyanta.pos.payment.dto.CreatePaymentMethodRequest;
import com.devkanyanta.pos.payment.dto.PaymentMethodResponse;
import com.devkanyanta.pos.payment.dto.UpdatePaymentMethodRequest;

public interface PaymentMethodService {

    PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request);

    List<PaymentMethodResponse> getAllPaymentMethods();

    PaymentMethodResponse getPaymentMethodById(Long id);

    PaymentMethodResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request);

    void deletePaymentMethod(Long id);
}
