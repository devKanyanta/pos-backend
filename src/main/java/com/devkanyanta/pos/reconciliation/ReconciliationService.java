package com.devkanyanta.pos.reconciliation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.payment.PaymentMethodRepository;
import com.devkanyanta.pos.reconciliation.dto.ReconciliationRequest;
import com.devkanyanta.pos.reconciliation.dto.ReconciliationResponse;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * Cash-up: the user declares what was counted in the drawer and found on each mobile money
 * statement; the system compares it with the expected amounts and flags variances. Submitting
 * again for the same day replaces the earlier count (recount).
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    public static final String BALANCED = "BALANCED";
    public static final String SHORT = "SHORT";
    public static final String OVER = "OVER";
    public static final String MISMATCH = "MISMATCH";

    private final ReconciliationRepository reconciliationRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final ZReportService zReportService;

    private final MerchantProfileService merchantProfileService;

    private final PosProperties properties;

    private final Clock clock;

    @Transactional
    public ReconciliationResponse submit(ReconciliationRequest request, String username) {

        requirePlan();

        if (request == null) {
            throw new BadRequestException("Reconciliation request is required");
        }

        LocalDate date = request.getBusinessDate() != null ? request.getBusinessDate() : LocalDate.now(clock);
        if (date.isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("Cannot reconcile a future date");
        }

        BigDecimal openingFloat = Money.of(Money.nz(request.getOpeningFloat()));
        if (openingFloat.signum() < 0) {
            throw new BadRequestException("Opening float cannot be negative");
        }

        Map<Long, BigDecimal> declared = new HashMap<>();
        if (request.getDeclared() != null) {
            for (ReconciliationRequest.Declared line : request.getDeclared()) {
                if (line == null || line.getPaymentMethodId() == null) {
                    throw new BadRequestException("Each declared amount needs a paymentMethodId");
                }
                if (line.getAmount() == null || line.getAmount().signum() < 0) {
                    throw new BadRequestException("Declared amounts must be zero or positive");
                }
                if (!paymentMethodRepository.existsById(line.getPaymentMethodId())) {
                    throw new NotFoundException("Payment method not found: " + line.getPaymentMethodId());
                }
                declared.merge(line.getPaymentMethodId(), Money.of(line.getAmount()), BigDecimal::add);
            }
        }

        List<ZReportService.TenderTotals> tenders = zReportService.computeTenders(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), openingFloat);

        // Tenders that were declared but had no activity still get a line.
        for (Long methodId : declared.keySet()) {
            boolean present = tenders.stream().anyMatch(t -> t.method().getId().equals(methodId));
            if (!present) {
                tenders = new java.util.ArrayList<>(tenders);
                tenders.add(new ZReportService.TenderTotals(paymentMethodRepository.findById(methodId).orElseThrow(),
                        0, Money.ZERO, Money.ZERO, Money.ZERO));
            }
        }

        ReconciliationEntity reconciliation = reconciliationRepository.findByBusinessDate(date)
                .orElseGet(() -> ReconciliationEntity.builder().businessDate(date).build());

        reconciliation.getLines().clear();

        BigDecimal tolerance = Money.nz(properties.getReconciliation().getVarianceTolerance()).abs();
        BigDecimal expectedTotal = Money.ZERO;
        BigDecimal declaredTotal = Money.ZERO;
        boolean anyFlagged = false;

        for (ZReportService.TenderTotals tender : tenders) {
            BigDecimal declaredAmount = declared.getOrDefault(tender.method().getId(), Money.ZERO);
            BigDecimal variance = declaredAmount.subtract(tender.expected());
            boolean flagged = variance.abs().compareTo(tolerance) > 0;
            anyFlagged |= flagged;

            reconciliation.getLines().add(ReconciliationLineEntity.builder()
                    .reconciliation(reconciliation)
                    .paymentMethod(tender.method())
                    .transactionCount((int) tender.count())
                    .expectedAmount(tender.expected())
                    .declaredAmount(declaredAmount)
                    .variance(variance)
                    .flagged(flagged)
                    .build());

            expectedTotal = expectedTotal.add(tender.expected());
            declaredTotal = declaredTotal.add(declaredAmount);
        }

        BigDecimal varianceTotal = declaredTotal.subtract(expectedTotal);
        String status;
        if (!anyFlagged) {
            status = BALANCED;
        } else if (varianceTotal.signum() < 0) {
            status = SHORT;
        } else if (varianceTotal.signum() > 0) {
            status = OVER;
        } else {
            status = MISMATCH;
        }

        reconciliation.setOpeningFloat(openingFloat);
        reconciliation.setExpectedTotal(expectedTotal);
        reconciliation.setDeclaredTotal(declaredTotal);
        reconciliation.setVarianceTotal(varianceTotal);
        reconciliation.setStatus(status);
        reconciliation.setNotes(Texts.truncate(Texts.clean(request.getNotes()), 1000));
        reconciliation.setSubmittedBy(username);
        reconciliation.setSubmittedAt(LocalDateTime.now(clock));

        return toResponse(reconciliationRepository.save(reconciliation));
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse getByDate(LocalDate date) {

        requirePlan();
        return reconciliationRepository.findByBusinessDate(date)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No reconciliation submitted for " + date));
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResponse> list(LocalDate from, LocalDate to) {

        requirePlan();
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        return reconciliationRepository.findByBusinessDateBetweenOrderByBusinessDateDesc(start, end).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requirePlan() {
        merchantProfileService.requireFeature(MerchantProfileService.FEATURE_RECONCILIATION, "End-of-day reconciliation");
    }

    private ReconciliationResponse toResponse(ReconciliationEntity entity) {

        return ReconciliationResponse.builder()
                .id(entity.getId())
                .businessDate(entity.getBusinessDate())
                .openingFloat(entity.getOpeningFloat())
                .expectedTotal(entity.getExpectedTotal())
                .declaredTotal(entity.getDeclaredTotal())
                .varianceTotal(entity.getVarianceTotal())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .submittedBy(entity.getSubmittedBy())
                .submittedAt(entity.getSubmittedAt())
                .lines(entity.getLines().stream()
                        .map(line -> ReconciliationResponse.Line.builder()
                                .paymentMethodId(line.getPaymentMethod().getId())
                                .paymentMethodName(line.getPaymentMethod().getName())
                                .transactionCount(line.getTransactionCount())
                                .expectedAmount(line.getExpectedAmount())
                                .declaredAmount(line.getDeclaredAmount())
                                .variance(line.getVariance())
                                .flagged(line.getFlagged())
                                .build())
                        .toList())
                .build();
    }
}
