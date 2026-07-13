package com.devkanyanta.pos.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.payment.dto.PaymentResponse;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleEntity.PaymentStatus;
import com.devkanyanta.pos.sale.SaleNumberGenerator;
import com.devkanyanta.pos.sale.SaleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    private final SaleRepository saleRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final ReceiptRepository receiptRepository;

    private final SaleNumberGenerator saleNumberGenerator;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long saleId, CreatePaymentRequest request) {

        SaleEntity sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (sale.getSaleStatus() != SaleEntity.SaleStatus.ACTIVE) {
            throw new RuntimeException("Cannot record payment for a " + sale.getSaleStatus() + " sale");
        }

        if (sale.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new RuntimeException("Cannot record payment for a refunded sale");
        }

        PaymentMethodEntity method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(() -> new RuntimeException("Payment method not found"));

        if (!method.getIsActive()) {
            throw new RuntimeException("Payment method is not active");
        }

        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new RuntimeException("Payment amount must be positive");
        }

        // Calculate total existing payments
        List<PaymentEntity> existingPayments = paymentRepository.findBySaleId(saleId);
        BigDecimal totalPaid = existingPayments.stream()
                .filter(p -> p.getPaymentStatus() == PaymentEntity.PaymentStatus.SUCCESS)
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = totalPaid.add(request.getAmount());

        if (newTotal.compareTo(sale.getTotalAmount()) > 0) {
            throw new RuntimeException(
                    "Payment would exceed sale total. Sale total: " + sale.getTotalAmount()
                    + ", already paid: " + totalPaid
                    + ", attempted: " + request.getAmount()
            );
        }

        // Create payment
        PaymentEntity payment = PaymentEntity.builder()
                .sale(sale)
                .paymentMethod(method)
                .amount(request.getAmount())
                .transactionReference(request.getTransactionReference())
                .build();

        paymentRepository.save(payment);

        // Update sale payment status
        if (newTotal.compareTo(sale.getTotalAmount()) >= 0) {
            sale.setPaymentStatus(PaymentStatus.PAID);

            // Auto-generate receipt when sale becomes fully paid
            if (receiptRepository.findBySaleId(saleId).isEmpty()) {
                ReceiptEntity receipt = ReceiptEntity.builder()
                        .sale(sale)
                        .receiptNumber(saleNumberGenerator.generateReceiptNumber())
                        .build();
                receiptRepository.save(receipt);
            }
        } else {
            sale.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        }

        saleRepository.save(sale);

        return toResponse(payment);
    }

    @Override
    public List<PaymentResponse> getPaymentsBySaleId(Long saleId) {

        return paymentRepository.findBySaleId(saleId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse toResponse(PaymentEntity payment) {

        return PaymentResponse.builder()
                .id(payment.getId())
                .saleId(payment.getSale().getId())
                .paymentMethodId(payment.getPaymentMethod().getId())
                .paymentMethodName(payment.getPaymentMethod().getName())
                .amount(payment.getAmount())
                .transactionReference(payment.getTransactionReference())
                .paymentStatus(payment.getPaymentStatus().name())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
