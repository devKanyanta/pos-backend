package com.devkanyanta.pos.payment;

import java.util.Comparator;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.payment.dto.CreatePaymentMethodRequest;
import com.devkanyanta.pos.payment.dto.PaymentMethodResponse;
import com.devkanyanta.pos.payment.dto.UpdatePaymentMethodRequest;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    /** ZRA VSDC payment type codes (pmtTyCd). */
    static final String ZRA_CASH = "01";
    static final String ZRA_CARD = "05";
    static final String ZRA_MOBILE_MONEY = "06";
    static final String ZRA_BANK_TRANSFER = "07";
    static final String ZRA_OTHER = "08";

    private final PaymentMethodRepository paymentMethodRepository;

    private final PaymentRepository paymentRepository;

    @Bean
    public CommandLineRunner seedPaymentMethods() {
        return args -> {
            seedIfNotExists("Cash", "CASH", PaymentMethodType.CASH);
            seedIfNotExists("MTN Mobile Money", "MTN", PaymentMethodType.MOBILE_MONEY);
            seedIfNotExists("Airtel Money", "AIRTEL", PaymentMethodType.MOBILE_MONEY);
            seedIfNotExists("Zamtel Kwacha", "ZAMTEL", PaymentMethodType.MOBILE_MONEY);
            seedIfNotExists("Card", "CARD", PaymentMethodType.CARD);
        };
    }

    private void seedIfNotExists(String name, String code, PaymentMethodType type) {

        PaymentMethodEntity method = paymentMethodRepository.findByCode(code)
                .or(() -> paymentMethodRepository.findByName(name))
                .orElseGet(() -> PaymentMethodEntity.builder()
                        .name(name)
                        .code(code)
                        .build());

        // Back-fill tender metadata on methods created by earlier versions.
        if (method.getType() == null) {
            method.setType(type);
        }
        if (method.getRequiresReference() == null) {
            method.setRequiresReference(type == PaymentMethodType.MOBILE_MONEY);
        }
        if (method.getZraPaymentTypeCode() == null) {
            method.setZraPaymentTypeCode(defaultZraCode(method.getType()));
        }

        paymentMethodRepository.save(method);
    }

    @Override
    @Transactional
    public PaymentMethodResponse createPaymentMethod(CreatePaymentMethodRequest request) {

        validateRequest(request);

        String name = request.getName().trim();
        String code = request.getCode().trim().toUpperCase();

        if (paymentMethodRepository.existsByName(name)) {
            throw new ConflictException("Payment method name already exists");
        }

        if (paymentMethodRepository.existsByCode(code)) {
            throw new ConflictException("Payment method code already exists");
        }

        PaymentMethodType type = parseType(request.getType(), PaymentMethodType.OTHER);

        PaymentMethodEntity method = PaymentMethodEntity.builder()
                .name(name)
                .code(code)
                .type(type)
                .requiresReference(request.getRequiresReference() != null
                        ? request.getRequiresReference()
                        : type == PaymentMethodType.MOBILE_MONEY)
                .zraPaymentTypeCode(validateZraCode(request.getZraPaymentTypeCode(), defaultZraCode(type)))
                .build();

        return toResponse(paymentMethodRepository.save(method));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getAllPaymentMethods() {

        return paymentMethodRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(PaymentMethodEntity::getId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodResponse getPaymentMethodById(Long id) {

        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public PaymentMethodResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request) {

        if (request == null) {
            throw new BadRequestException("Payment method request is required");
        }

        PaymentMethodEntity method = findById(id);

        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (!name.equals(method.getName())
                    && paymentMethodRepository.existsByName(name)) {
                throw new ConflictException("Payment method name already exists");
            }
            method.setName(name);
        }

        if (StringUtils.hasText(request.getCode())) {
            String code = request.getCode().trim().toUpperCase();
            if (!code.equals(method.getCode())
                    && paymentMethodRepository.existsByCode(code)) {
                throw new ConflictException("Payment method code already exists");
            }
            method.setCode(code);
        }

        if (request.getIsActive() != null) {
            method.setIsActive(request.getIsActive());
        }

        if (StringUtils.hasText(request.getType())) {
            method.setType(parseType(request.getType(), method.getType()));
        }

        if (request.getRequiresReference() != null) {
            method.setRequiresReference(request.getRequiresReference());
        }

        if (request.getZraPaymentTypeCode() != null) {
            method.setZraPaymentTypeCode(validateZraCode(request.getZraPaymentTypeCode(), defaultZraCode(method.getType())));
        }

        return toResponse(paymentMethodRepository.save(method));
    }

    /** Methods already used by payments are deactivated rather than deleted. */
    @Override
    @Transactional
    public void deletePaymentMethod(Long id) {

        PaymentMethodEntity method = findById(id);

        if (paymentRepository.existsByPaymentMethodId(id)) {
            method.setIsActive(false);
            paymentMethodRepository.save(method);
            return;
        }

        paymentMethodRepository.delete(method);
    }

    private PaymentMethodEntity findById(Long id) {

        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment method not found"));
    }

    private void validateRequest(CreatePaymentMethodRequest request) {

        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("Payment method name is required");
        }

        if (!StringUtils.hasText(request.getCode())) {
            throw new BadRequestException("Payment method code is required");
        }
    }

    private static PaymentMethodType parseType(String raw, PaymentMethodType fallback) {

        if (!StringUtils.hasText(raw)) {
            return fallback;
        }

        try {
            return PaymentMethodType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Payment method type must be one of CASH, MOBILE_MONEY, CARD, BANK_TRANSFER, OTHER");
        }
    }

    private static String validateZraCode(String raw, String fallback) {

        if (!StringUtils.hasText(raw)) {
            return fallback;
        }

        String code = raw.trim();
        if (!code.matches("\\d{2}")) {
            throw new BadRequestException("ZRA payment type code must be two digits, e.g. 01");
        }
        return code;
    }

    static String defaultZraCode(PaymentMethodType type) {

        if (type == null) {
            return ZRA_OTHER;
        }

        return switch (type) {
            case CASH -> ZRA_CASH;
            case CARD -> ZRA_CARD;
            case MOBILE_MONEY -> ZRA_MOBILE_MONEY;
            case BANK_TRANSFER -> ZRA_BANK_TRANSFER;
            case OTHER -> ZRA_OTHER;
        };
    }

    private PaymentMethodResponse toResponse(PaymentMethodEntity method) {

        return PaymentMethodResponse.builder()
                .id(method.getId())
                .name(method.getName())
                .code(method.getCode())
                .isActive(method.getIsActive())
                .type(method.getType() != null ? method.getType().name() : null)
                .requiresReference(Boolean.TRUE.equals(method.getRequiresReference()))
                .zraPaymentTypeCode(method.getZraPaymentTypeCode())
                .build();
    }
}
