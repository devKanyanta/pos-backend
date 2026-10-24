package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReceiptLineRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReceiptLineResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReceiptRequest;
import com.devkanyanta.pos.sale.SaleNumberGenerator;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.shared.web.PageResponse;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Receiving goods against a purchase order: the only way purchased stock enters the ledger.
 *
 * <p>Rules: a receipt may not exceed what is still outstanding on a line; several receipts may be booked
 * against one order (partial deliveries); the order moves to PARTIALLY_RECEIVED / RECEIVED automatically;
 * each receipt raises a PURCHASE_RECEIPT movement per product valued at the delivery's unit cost, which
 * re-averages the product's weighted-average cost and becomes its latest purchase cost. A receipt is
 * immutable and idempotent on its {@code requestId}.
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    static final String REQUEST_ID_PATTERN = "[A-Za-z0-9._:\\-]{8,64}";

    private static final Map<String, String> SORTS = Map.of("receivedAt", "receivedAt", "receiptNumber", "receiptNumber");

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final GoodsReceiptRepository goodsReceiptRepository;

    private final ProductRepository productRepository;

    private final StockLedger stockLedger;

    private final SaleNumberGenerator numberGenerator;

    @Transactional
    public GoodsReceiptResponse receive(Long purchaseOrderId, ReceiptRequest request) {

        if (request == null) {
            throw new BadRequestException("Receipt request is required");
        }
        String requestId = Texts.clean(request.requestId());
        if (requestId == null || !requestId.matches(REQUEST_ID_PATTERN)) {
            throw new BadRequestException("requestId is required: 8-64 characters (letters, digits, . _ : -)");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("A receipt needs at least one line");
        }

        var replay = replayOf(requestId, purchaseOrderId);
        if (replay != null) {
            return replay;
        }

        PurchaseOrderEntity order = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("Purchase order not found"));

        // A concurrent request with the same key may have finished while this one waited for the lock.
        replay = replayOf(requestId, purchaseOrderId);
        if (replay != null) {
            return replay;
        }

        if (order.getStatus() != PurchaseOrderEntity.Status.ORDERED
                && order.getStatus() != PurchaseOrderEntity.Status.PARTIALLY_RECEIVED) {
            throw new ConflictException("Goods cannot be received against an order that is " + order.getStatus());
        }

        String user = CurrentUser.username();
        GoodsReceiptEntity receipt = GoodsReceiptEntity.builder()
                .receiptNumber(numberGenerator.generateGoodsReceiptNumber())
                .purchaseOrder(order)
                .supplier(order.getSupplier())
                .requestId(requestId)
                .supplierInvoiceNumber(Texts.truncate(Texts.clean(request.supplierInvoiceNumber()), 100))
                .notes(Texts.truncate(Texts.clean(request.notes()), 1000))
                .receivedBy(user)
                .build();

        // Resolve and validate every line before anything changes.
        Map<Long, GoodsReceiptLineEntity> byProduct = new TreeMap<>();
        Set<Long> usedLines = new HashSet<>();
        for (ReceiptLineRequest line : request.lines()) {
            PurchaseOrderLineEntity poLine = resolveLine(order, line);
            if (!usedLines.add(poLine.getId())) {
                throw new BadRequestException("Order line for '" + poLine.getProduct().getName()
                        + "' appears more than once in this receipt");
            }
            if (line.quantity() == null || line.quantity() <= 0) {
                throw new BadRequestException("Received quantity must be positive");
            }
            if (line.quantity() > poLine.getOutstandingQuantity()) {
                throw new BadRequestException("Cannot receive " + line.quantity() + " of '" + poLine.getProduct().getName()
                        + "': only " + poLine.getOutstandingQuantity() + " outstanding (ordered "
                        + poLine.getOrderedQuantity() + ", received " + poLine.getReceivedQuantity() + ")");
            }
            BigDecimal cost = line.unitCost() != null ? line.unitCost() : poLine.getUnitCost();
            if (cost.signum() < 0) {
                throw new BadRequestException("Unit cost cannot be negative");
            }
            byProduct.put(poLine.getProduct().getId(), GoodsReceiptLineEntity.builder()
                    .receipt(receipt)
                    .poLine(poLine)
                    .product(poLine.getProduct())
                    .quantity(line.quantity())
                    .unitCost(cost.setScale(4, RoundingMode.HALF_UP))
                    .build());
        }
        receipt.getLines().addAll(byProduct.values());
        goodsReceiptRepository.save(receipt);

        // Apply in product-id order so concurrent receipts lock rows consistently.
        for (GoodsReceiptLineEntity line : receipt.getLines()) {
            ProductEntity product = productRepository.findByIdForUpdate(line.getProduct().getId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            product.setCostPrice(line.getUnitCost().setScale(2, RoundingMode.HALF_UP));
            stockLedger.record(StockLedger.Change.builder()
                    .product(product).type(MovementType.PURCHASE_RECEIPT).quantity(line.getQuantity())
                    .unitCost(line.getUnitCost())
                    .referenceId(receipt.getId()).referenceType("GOODS_RECEIPT")
                    .notes("Received " + receipt.getReceiptNumber() + " on " + order.getPoNumber()
                            + " from " + order.getSupplier().getName())
                    .performedBy(user)
                    .build());
            line.getPoLine().setReceivedQuantity(line.getPoLine().getReceivedQuantity() + line.getQuantity());
        }

        boolean complete = order.getLines().stream().allMatch(l -> l.getOutstandingQuantity() == 0);
        order.setStatus(complete ? PurchaseOrderEntity.Status.RECEIVED : PurchaseOrderEntity.Status.PARTIALLY_RECEIVED);
        purchaseOrderRepository.save(order);

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(Long id) {
        return toResponse(goodsReceiptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Goods receipt not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptResponse> search(Long supplierId, Long purchaseOrderId, Long productId,
                                                     LocalDate from, LocalDate to, Integer page, Integer size, String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        java.time.LocalDateTime start = from != null ? from.atStartOfDay() : null;
        java.time.LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : null;
        Specification<GoodsReceiptEntity> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (supplierId != null) {
                all.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (purchaseOrderId != null) {
                all.add(cb.equal(root.get("purchaseOrder").get("id"), purchaseOrderId));
            }
            if (start != null) {
                all.add(cb.greaterThanOrEqualTo(root.<java.time.LocalDateTime>get("receivedAt"), start));
            }
            if (end != null) {
                all.add(cb.lessThan(root.<java.time.LocalDateTime>get("receivedAt"), end));
            }
            if (productId != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<GoodsReceiptLineEntity> line = sub.from(GoodsReceiptLineEntity.class);
                sub.select(line.<Long>get("id")).where(cb.equal(line.get("receipt"), root),
                        cb.equal(line.get("product").get("id"), productId));
                all.add(cb.exists(sub));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        return PageResponse.of(
                goodsReceiptRepository.findAll(spec,
                        PageResponse.pageable(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "id"))),
                this::toResponse);
    }

    private GoodsReceiptResponse replayOf(String requestId, Long purchaseOrderId) {
        return goodsReceiptRepository.findByRequestId(requestId).map(existing -> {
            if (!existing.getPurchaseOrder().getId().equals(purchaseOrderId)) {
                throw new ConflictException("requestId " + requestId + " was already used for another purchase order");
            }
            return toResponse(existing);
        }).orElse(null);
    }

    private PurchaseOrderLineEntity resolveLine(PurchaseOrderEntity order, ReceiptLineRequest line) {
        if (line == null || (line.poLineId() == null && line.productId() == null)) {
            throw new BadRequestException("Each receipt line needs a poLineId or productId");
        }
        List<PurchaseOrderLineEntity> matches = new ArrayList<>();
        for (PurchaseOrderLineEntity candidate : order.getLines()) {
            boolean byLine = line.poLineId() != null && candidate.getId().equals(line.poLineId());
            boolean byProduct = line.poLineId() == null && candidate.getProduct().getId().equals(line.productId());
            if (byLine || byProduct) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            throw new BadRequestException("Line " + (line.poLineId() != null ? line.poLineId() : "for product " + line.productId())
                    + " does not belong to " + order.getPoNumber());
        }
        return matches.get(0);
    }

    GoodsReceiptResponse toResponse(GoodsReceiptEntity r) {
        List<ReceiptLineResponse> lines = r.getLines().stream().map(l -> new ReceiptLineResponse(
                l.getId(), l.getPoLine().getId(), l.getProduct().getId(), l.getProduct().getName(),
                l.getQuantity(), l.getUnitCost(), l.getTotalCost())).toList();
        return new GoodsReceiptResponse(r.getId(), r.getReceiptNumber(), r.getPurchaseOrder().getId(),
                r.getPurchaseOrder().getPoNumber(), r.getSupplier().getId(), r.getSupplier().getName(),
                r.getSupplierInvoiceNumber(), r.getNotes(), r.getReceivedBy(), r.getReceivedAt(), r.getTotalCost(),
                r.getPurchaseOrder().getStatus().name(), lines);
    }
}
