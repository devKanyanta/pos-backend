package com.devkanyanta.pos.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.payment.dto.CreatePaymentRequest;
import com.devkanyanta.pos.payment.dto.PaymentResponse;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.receipt.ReceiptRepository;
import com.devkanyanta.pos.sale.SaleCompletionService;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleEntity.PaymentStatus;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    public static final String CONFLICT_MISSING_REFERENCE = "MISSING_PAYMENT_REFERENCE";
    public static final String CONFLICT_DUPLICATE_REFERENCE = "DUPLICATE_PAYMENT_REFERENCE";
    public static final String CONFLICT_OVERPAYMENT = "OVERPAYMENT";
    public static final String CONFLICT_INACTIVE_METHOD = "INACTIVE_PAYMENT_METHOD";

    private final PaymentRepository paymentRepository;

    private final SaleRepository saleRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final ReceiptRepository receiptRepository;

    private final SaleCompletionService saleCompletionService;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long saleId, CreatePaymentRequest request) {

        SaleEntity sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));

        PaymentOutcome outcome = applyPayment(sale, request, CurrentUser.username(), null, false);

        return toResponse(outcome.payment());
    }

    @Override
    @Transactional
    public PaymentOutcome applyPayment(
            SaleEntity sale,
            CreatePaymentRequest request,
            String username,
            LocalDateTime paidAt,
            boolean offline
    ) {
        List<String> conflicts = new ArrayList<>();

        if (sale.getSaleStatus() != SaleEntity.SaleStatus.ACTIVE) {
            throw new BadRequestException("Cannot record payment for a " + sale.getSaleStatus() + " sale");
        }

        if (sale.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Cannot record payment for a refunded sale");
        }

        if (request == null || request.getPaymentMethodId() == null) {
            throw new BadRequestException("Payment method is required");
        }

        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BadRequestException("Payment amount must be positive");
        }

        PaymentMethodEntity method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(() -> new NotFoundException("Payment method not found"));

        if (!Boolean.TRUE.equals(method.getIsActive())) {
            if (!offline) {
                throw new BadRequestException("Payment method is not active");
            }
            conflicts.add(CONFLICT_INACTIVE_METHOD);
        }

        BigDecimal amount = Money.of(request.getAmount());
        BigDecimal alreadyPaid = paidAmount(sale.getId());
        BigDecimal remaining = Money.of(sale.getTotalAmount()).subtract(alreadyPaid);

        if (remaining.signum() <= 0) {
            if (!offline) {
                throw new BadRequestException("Sale is already fully paid");
            }
            conflicts.add(CONFLICT_OVERPAYMENT);
            return new PaymentOutcome(null, conflicts);
        }

        String reference = normalizeReference(request.getTransactionReference());

        if (reference == null && Boolean.TRUE.equals(method.getRequiresReference())) {
            if (!offline) {
                throw new BadRequestException("A transaction reference (confirmation code) is required for " + method.getName());
            }
            conflicts.add(CONFLICT_MISSING_REFERENCE);
        }

        if (reference != null && paymentRepository.existsActiveReference(method.getId(), reference)) {
            if (!offline) {
                throw new ConflictException("Transaction reference " + reference + " has already been used for " + method.getName());
            }
            conflicts.add(CONFLICT_DUPLICATE_REFERENCE);
        }

        BigDecimal applied = amount;
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal tendered = null;

        if (amount.compareTo(remaining) > 0) {
            if (method.getType() == PaymentMethodType.CASH) {
                applied = remaining;
                change = amount.subtract(remaining);
                tendered = amount;
            } else if (offline) {
                applied = remaining;
                conflicts.add(CONFLICT_OVERPAYMENT);
            } else {
                throw new BadRequestException(
                        "Payment would exceed sale total. Sale total: " + sale.getTotalAmount()
                        + ", already paid: " + alreadyPaid
                        + ", attempted: " + amount
                );
            }
        }

        PaymentEntity payment = PaymentEntity.builder()
                .sale(sale)
                .paymentMethod(method)
                .amount(applied)
                .amountTendered(tendered)
                .changeAmount(Money.of(change))
                .transactionReference(reference)
                .payerPhone(Texts.truncate(Texts.clean(request.getPayerPhone()), 20))
                .recordedBy(username != null ? username : "system")
                .paidAt(paidAt)
                .build();

        paymentRepository.save(payment);
        sale.getPayments().add(payment);

        BigDecimal newTotal = alreadyPaid.add(applied);

        if (newTotal.compareTo(Money.of(sale.getTotalAmount())) >= 0) {
            sale.setPaymentStatus(PaymentStatus.PAID);
            saleRepository.save(sale);
            saleCompletionService.onFullyPaid(sale);
        } else {
            sale.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
            saleRepository.save(sale);
        }

        return new PaymentOutcome(payment, conflicts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsBySaleId(Long saleId) {

        if (!saleRepository.existsById(saleId)) {
            throw new NotFoundException("Sale not found");
        }

        return paymentRepository.findBySaleIdOrderByIdAsc(saleId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private BigDecimal paidAmount(Long saleId) {

        return paymentRepository.findBySaleId(saleId).stream()
                .filter(p -> p.getPaymentStatus() != PaymentEntity.PaymentStatus.FAILED)
                .map(PaymentEntity::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private static String normalizeReference(String raw) {

        String reference = Texts.clean(raw);
        if (reference == null) {
            return null;
        }

        reference = reference.toUpperCase();
        if (!reference.matches("[A-Z0-9][A-Z0-9.\\-_/#]{2,63}")) {
            throw new BadRequestException("Transaction reference must be 3-64 letters, digits or . - _ / #");
        }
        return reference;
    }

    @Override
    public PaymentResponse toResponse(PaymentEntity payment) {

        SaleEntity sale = payment.getSale();
        BigDecimal paid = paidAmount(sale.getId());
        ReceiptEntity receipt = receiptRepository.findBySaleId(sale.getId()).orElse(null);
        PaymentMethodEntity method = payment.getPaymentMethod();

        return PaymentResponse.builder()
                .id(payment.getId())
                .saleId(sale.getId())
                .paymentMethodId(method.getId())
                .paymentMethodName(method.getName())
                .paymentMethodCode(method.getCode())
                .paymentMethodType(method.getType() != null ? method.getType().name() : null)
                .amount(payment.getAmount())
                .amountTendered(payment.getAmountTendered())
                .changeAmount(payment.getChangeAmount())
                .refundedAmount(payment.getRefundedAmount())
                .transactionReference(payment.getTransactionReference())
                .payerPhone(payment.getPayerPhone())
                .paymentStatus(payment.getPaymentStatus().name())
                .recordedBy(payment.getRecordedBy())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .saleBalanceDue(Money.of(sale.getTotalAmount()).subtract(paid).max(Money.ZERO))
                .salePaymentStatus(sale.getPaymentStatus().name())
                .receiptId(receipt != null ? receipt.getId() : null)
                .receiptNumber(receipt != null ? receipt.getReceiptNumber() : null)
                .build();
    }
}
