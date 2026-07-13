package com.devkanyanta.pos.payment;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.payment.dto.CreatePaymentMethodRequest;
import com.devkanyanta.pos.payment.dto.PaymentMethodResponse;
import com.devkanyanta.pos.payment.dto.UpdatePaymentMethodRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    @Bean
    public CommandLineRunner seedPaymentMethods() {
        return args -> {
            seedIfNotExists("Cash", "CASH");
            seedIfNotExists("MTN Mobile Money", "MTN");
            seedIfNotExists("Airtel Money", "AIRTEL");
            seedIfNotExists("Card", "CARD");
        };
    }

    private void seedIfNotExists(String name, String code) {

        if (!paymentMethodRepository.existsByName(name)) {
            paymentMethodRepository.save(
                    PaymentMethodEntity.builder()
                            .name(name)
                            .code(code)
                            .build()
            );
        }
    }

    @Override
    public PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request) {

        validateRequest(request);

        if (paymentMethodRepository.existsByName(request.getName())) {
            throw new RuntimeException("Payment method name already exists");
        }

        if (paymentMethodRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Payment method code already exists");
        }

        PaymentMethodEntity method = PaymentMethodEntity.builder()
                .name(request.getName())
                .code(request.getCode())
                .build();

        return toResponse(paymentMethodRepository.save(method));
    }

    @Override
    public List<PaymentMethodResponse> getAllPaymentMethods() {

        return paymentMethodRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaymentMethodResponse getPaymentMethodById(Long id) {

        return toResponse(findById(id));
    }

    @Override
    public PaymentMethodResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request) {

        PaymentMethodEntity method = findById(id);

        if (StringUtils.hasText(request.getName())) {
            if (!request.getName().equals(method.getName())
                    && paymentMethodRepository.existsByName(request.getName())) {
                throw new RuntimeException("Payment method name already exists");
            }
            method.setName(request.getName());
        }

        if (StringUtils.hasText(request.getCode())) {
            if (!request.getCode().equals(method.getCode())
                    && paymentMethodRepository.existsByCode(request.getCode())) {
                throw new RuntimeException("Payment method code already exists");
            }
            method.setCode(request.getCode());
        }

        if (request.getIsActive() != null) {
            method.setIsActive(request.getIsActive());
        }

        return toResponse(paymentMethodRepository.save(method));
    }

    @Override
    public void deletePaymentMethod(Long id) {

        PaymentMethodEntity method = findById(id);
        paymentMethodRepository.delete(method);
    }

    private PaymentMethodEntity findById(Long id) {

        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment method not found"));
    }

    private void validateRequest(CreatePaymentMethodRequest request) {

        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new RuntimeException("Payment method name is required");
        }

        if (!StringUtils.hasText(request.getCode())) {
            throw new RuntimeException("Payment method code is required");
        }
    }

    private PaymentMethodResponse toResponse(PaymentMethodEntity method) {

        return PaymentMethodResponse.builder()
                .id(method.getId())
                .name(method.getName())
                .code(method.getCode())
                .isActive(method.getIsActive())
                .build();
    }
}
