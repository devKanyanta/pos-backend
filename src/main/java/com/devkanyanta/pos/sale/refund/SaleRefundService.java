package com.devkanyanta.pos.sale.refund;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentMethodEntity;
import com.devkanyanta.pos.payment.PaymentMethodRepository;
import com.devkanyanta.pos.payment.PaymentRepository;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.SaleEntity.PaymentStatus;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.sale.SaleItemEntity;
import com.devkanyanta.pos.sale.SaleItemEntity.ItemStatus;
import com.devkanyanta.pos.sale.SaleNumberGenerator;
import com.devkanyanta.pos.sale.SaleRepository;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.zra.ZraQueueService;

import lombok.RequiredArgsConstructor;

/**
 * Full and partial (per quantity) refunds of paid sales.
 *
 * <p>Each refund creates a credit note record, returns stock (RETURN movements), records how the
 * money went back to the customer (per tender, for reconciliation), updates the refunded
 * amounts on the original payments and queues a ZRA credit note.
 */
@Service
@RequiredArgsConstructor
public class SaleRefundService {

    private final SaleRepository saleRepository;

    private final SaleRefundRepository saleRefundRepository;

    private final PaymentRepository paymentRepository;

    private final PaymentMethodRepository paymentMethodRepository;

    private final ProductRepository productRepository;

    private final StockLedger stockLedger;

    private final SaleNumberGenerator saleNumberGenerator;

    private final ZraQueueService zraQueueService;

    private final PosProperties properties;

    @Transactional
    public SaleEntity refund(Long saleId, RefundRequest request, String username) {

        SaleEntity sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));

        if (sale.getSaleStatus() == SaleStatus.VOIDED) {
            throw new BadRequestException("Cannot refund a voided sale");
        }

        if (sale.getSaleStatus() == SaleStatus.REFUNDED) {
            throw new BadRequestException("Sale is already refunded");
        }

        if (sale.getPaymentStatus() != PaymentStatus.PAID && sale.getPaymentStatus() != PaymentStatus.PARTIALLY_PAID) {
            throw new BadRequestException("Only paid sales can be refunded. Void unpaid sales instead.");
        }

        Map<SaleItemEntity, Integer> quantities = resolveQuantities(sale, request);

        String zraReasonCode = StringUtils.hasText(request.getZraReasonCode())
                ? request.getZraReasonCode().trim()
                : properties.getZra().getDefaultRefundReasonCode();

        if (!zraReasonCode.matches("\\d{2}")) {
            throw new BadRequestException("ZRA refund reason code must be two digits");
        }

        SaleRefundEntity refund = SaleRefundEntity.builder()
                .refundNumber(saleNumberGenerator.generateRefundNumber())
                .sale(sale)
                .reason(Texts.truncate(Texts.clean(request.getReason()), 255))
                .zraReasonCode(zraReasonCode)
                .createdBy(username != null ? username : "system")
                .totalAmount(Money.ZERO)
                .vatAmount(Money.ZERO)
                .build();

        BigDecimal refundTotal = Money.ZERO;
        BigDecimal refundVat = Money.ZERO;
        Map<Long, Integer> stockReturns = new HashMap<>();
        Map<Long, Integer> damagedReturns = new HashMap<>();
        Map<Long, BigDecimal> returnCosts = new HashMap<>();
        Map<Long, Integer> damagedByItem = resolveDamaged(request, quantities);

        for (Map.Entry<SaleItemEntity, Integer> entry : quantities.entrySet()) {

            SaleItemEntity item = entry.getKey();
            int quantity = entry.getValue();
            boolean finalRefund = quantity == item.getRemainingQuantity();

            BigDecimal itemTotal = item.getTotalInclVat();
            BigDecimal itemVat = Money.nz(item.getVatAmount());
            BigDecimal amount;
            BigDecimal vat;

            if (finalRefund) {
                // The last refund of a line takes the exact remainder, so rounding never drifts.
                amount = itemTotal.subtract(Money.nz(item.getRefundedAmount()));
                vat = itemVat.subtract(Money.nz(item.getRefundedVatAmount()));
            } else {
                amount = prorate(itemTotal, quantity, item.getQuantity());
                vat = prorate(itemVat, quantity, item.getQuantity());
            }

            item.setRefundedQuantity(item.getRefundedQuantity() + quantity);
            item.setRefundedAmount(Money.nz(item.getRefundedAmount()).add(amount));
            item.setRefundedVatAmount(Money.nz(item.getRefundedVatAmount()).add(vat));
            if (item.getRemainingQuantity() == 0) {
                item.setItemStatus(ItemStatus.REFUNDED);
            }

            refund.getItems().add(SaleRefundItemEntity.builder()
                    .refund(refund)
                    .saleItem(item)
                    .quantity(quantity)
                    .taxableAmount(amount.subtract(vat))
                    .vatAmount(vat)
                    .totalAmount(amount)
                    .build());

            refundTotal = refundTotal.add(amount);
            refundVat = refundVat.add(vat);

            if (item.getProduct() != null && Boolean.TRUE.equals(item.getProduct().getTrackInventory())) {
                Long productId = item.getProduct().getId();
                stockReturns.merge(productId, quantity, Integer::sum);
                damagedReturns.merge(productId, damagedByItem.getOrDefault(item.getId(), 0), Integer::sum);
                if (item.getUnitCost() != null) {
                    returnCosts.merge(productId, item.getUnitCost().multiply(BigDecimal.valueOf(quantity)), BigDecimal::add);
                }
            }
        }

        refund.setTotalAmount(refundTotal);
        refund.setVatAmount(refundVat);

        allocatePayouts(sale, refund, request.getRefundPaymentMethodId());

        saleRefundRepository.save(refund);

        for (Long productId : new TreeSet<>(stockReturns.keySet())) {
            ProductEntity product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
            int returned = stockReturns.get(productId);
            // Goods come back at the cost they left at, so a refund does not distort the average.
            BigDecimal cost = returnCosts.containsKey(productId)
                    ? returnCosts.get(productId).divide(BigDecimal.valueOf(returned), 4, java.math.RoundingMode.HALF_UP)
                    : null;
            String note = "Refund " + refund.getRefundNumber() + " of " + sale.getSaleNumber()
                    + (refund.getReason() != null ? " - " + refund.getReason() : "");
            stockLedger.record(StockLedger.Change.builder()
                    .product(product).type(MovementType.RETURN).quantity(returned).unitCost(cost)
                    .referenceId(sale.getId()).referenceType("SALE_REFUND")
                    .notes(note).performedBy(refund.getCreatedBy())
                    .build());

            int damaged = damagedReturns.getOrDefault(productId, 0);
            if (damaged > 0) {
                // Damaged goods are not saleable: take them back, then write them off, so the loss is visible.
                stockLedger.record(StockLedger.Change.builder()
                        .product(product).type(MovementType.ADJUSTMENT).quantity(-damaged)
                        .referenceId(sale.getId()).referenceType("SALE_REFUND")
                        .reason("DAMAGED_RETURN")
                        .notes("Damaged goods returned: " + note).performedBy(refund.getCreatedBy())
                        .build());
            }
        }

        sale.setRefundedAmount(Money.nz(sale.getRefundedAmount()).add(refundTotal));

        boolean allRefunded = sale.getItems().stream()
                .allMatch(item -> item.getItemStatus() == ItemStatus.REFUNDED);

        if (allRefunded) {
            sale.setSaleStatus(SaleStatus.REFUNDED);
            sale.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        saleRepository.save(sale);

        zraQueueService.queueCreditNote(refund);

        return sale;
    }

    private Map<SaleItemEntity, Integer> resolveQuantities(SaleEntity sale, RefundRequest request) {

        Map<Long, SaleItemEntity> itemsById = new HashMap<>();
        sale.getItems().forEach(item -> itemsById.put(item.getId(), item));

        Map<SaleItemEntity, Integer> quantities = new LinkedHashMap<>();

        boolean explicit = (request.getSaleItemIds() != null && !request.getSaleItemIds().isEmpty())
                || (request.getItems() != null && !request.getItems().isEmpty());

        if (!explicit) {
            // Full refund of everything still refundable.
            sale.getItems().stream()
                    .filter(item -> item.getRemainingQuantity() > 0)
                    .forEach(item -> quantities.put(item, item.getRemainingQuantity()));
        } else {
            if (request.getSaleItemIds() != null) {
                for (Long itemId : request.getSaleItemIds()) {
                    SaleItemEntity item = requireItem(itemsById, itemId);
                    if (item.getRemainingQuantity() <= 0) {
                        throw new BadRequestException("Item already refunded: " + item.getProductName());
                    }
                    quantities.merge(item, item.getRemainingQuantity(), (a, b) -> a);
                }
            }

            if (request.getItems() != null) {
                for (RefundRequest.RefundItemRequest line : request.getItems()) {
                    if (line == null || line.getSaleItemId() == null) {
                        throw new BadRequestException("Each refund item needs a saleItemId");
                    }
                    if (line.getQuantity() == null || line.getQuantity() <= 0) {
                        throw new BadRequestException("Refund quantity must be positive");
                    }
                    SaleItemEntity item = requireItem(itemsById, line.getSaleItemId());
                    quantities.merge(item, line.getQuantity(), Integer::sum);
                }
            }
        }

        if (quantities.isEmpty()) {
            throw new BadRequestException("No valid sale items found to refund");
        }

        for (Map.Entry<SaleItemEntity, Integer> entry : quantities.entrySet()) {
            if (entry.getValue() > entry.getKey().getRemainingQuantity()) {
                throw new BadRequestException("Cannot refund " + entry.getValue() + " of '"
                        + entry.getKey().getProductName() + "': only "
                        + entry.getKey().getRemainingQuantity() + " remaining");
            }
        }

        return quantities;
    }

    /** Units per sale item that come back damaged (item id -> units), validated against what is refunded. */
    private Map<Long, Integer> resolveDamaged(RefundRequest request, Map<SaleItemEntity, Integer> quantities) {

        Map<Long, Integer> refundedNow = new HashMap<>();
        quantities.forEach((item, qty) -> refundedNow.put(item.getId(), qty));
        Map<Long, Integer> damaged = new HashMap<>();

        if (request.getDamagedSaleItemIds() != null) {
            for (Long itemId : request.getDamagedSaleItemIds()) {
                Integer qty = refundedNow.get(itemId);
                if (qty == null) {
                    throw new BadRequestException("Sale item " + itemId + " is marked damaged but is not being refunded");
                }
                damaged.put(itemId, qty);
            }
        }
        if (request.getItems() != null) {
            for (RefundRequest.RefundItemRequest line : request.getItems()) {
                String condition = line.getCondition() == null ? "SALEABLE" : line.getCondition().trim().toUpperCase();
                if (!condition.equals("SALEABLE") && !condition.equals("DAMAGED")) {
                    throw new BadRequestException("Refund item condition must be SALEABLE or DAMAGED");
                }
                if (condition.equals("DAMAGED")) {
                    damaged.merge(line.getSaleItemId(), line.getQuantity(), Integer::sum);
                }
            }
        }
        damaged.forEach((itemId, qty) -> {
            if (qty > refundedNow.getOrDefault(itemId, 0)) {
                throw new BadRequestException("More units marked damaged than are being refunded for sale item " + itemId);
            }
        });
        return damaged;
    }

    private SaleItemEntity requireItem(Map<Long, SaleItemEntity> itemsById, Long itemId) {

        SaleItemEntity item = itemsById.get(itemId);
        if (item == null) {
            throw new BadRequestException("Sale item " + itemId + " does not belong to this sale");
        }
        return item;
    }

    /**
     * Records how the refund is paid out. With an explicit tender the whole amount goes out in
     * that tender (linked to a payment of the same method when there is one); otherwise it is
     * returned against the sale's own payments, latest first.
     */
    private void allocatePayouts(SaleEntity sale, SaleRefundEntity refund, Long refundMethodId) {

        List<PaymentEntity> payments = new ArrayList<>(paymentRepository.findBySaleIdOrderByIdAsc(sale.getId()));
        payments.removeIf(p -> p.getPaymentStatus() == PaymentEntity.PaymentStatus.FAILED);
        payments.sort(Comparator.comparing(PaymentEntity::getId).reversed());

        BigDecimal refundable = payments.stream()
                .map(PaymentEntity::getRefundableAmount)
                .reduce(Money.ZERO, BigDecimal::add);

        BigDecimal amount = refund.getTotalAmount();

        if (amount.compareTo(refundable) > 0) {
            throw new BadRequestException("Refund amount " + amount + " exceeds the amount paid and not yet refunded ("
                    + refundable + ")");
        }

        if (refundMethodId != null) {
            PaymentMethodEntity method = paymentMethodRepository.findById(refundMethodId)
                    .orElseThrow(() -> new NotFoundException("Refund payment method not found"));

            // Reduce refundable balances, preferring payments made with the same tender.
            List<PaymentEntity> ordered = new ArrayList<>(payments);
            ordered.sort(Comparator.comparing((PaymentEntity p) -> !p.getPaymentMethod().getId().equals(method.getId())));

            BigDecimal left = amount;
            PaymentEntity linked = null;
            for (PaymentEntity payment : ordered) {
                if (left.signum() <= 0) {
                    break;
                }
                BigDecimal take = payment.getRefundableAmount().min(left);
                if (take.signum() > 0) {
                    applyRefundToPayment(payment, take);
                    left = left.subtract(take);
                    if (linked == null && payment.getPaymentMethod().getId().equals(method.getId())) {
                        linked = payment;
                    }
                }
            }

            refund.getPayments().add(SaleRefundPaymentEntity.builder()
                    .refund(refund)
                    .payment(linked)
                    .paymentMethod(method)
                    .amount(amount)
                    .build());
            return;
        }

        BigDecimal left = amount;
        for (PaymentEntity payment : payments) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = payment.getRefundableAmount().min(left);
            if (take.signum() > 0) {
                applyRefundToPayment(payment, take);
                refund.getPayments().add(SaleRefundPaymentEntity.builder()
                        .refund(refund)
                        .payment(payment)
                        .paymentMethod(payment.getPaymentMethod())
                        .amount(take)
                        .build());
                left = left.subtract(take);
            }
        }
    }

    private void applyRefundToPayment(PaymentEntity payment, BigDecimal amount) {

        payment.setRefundedAmount(Money.nz(payment.getRefundedAmount()).add(amount));
        if (payment.getRefundableAmount().signum() <= 0) {
            payment.setPaymentStatus(PaymentEntity.PaymentStatus.REFUNDED);
        }
        paymentRepository.save(payment);
    }

    private static BigDecimal prorate(BigDecimal amount, int part, int whole) {
        return amount.multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
