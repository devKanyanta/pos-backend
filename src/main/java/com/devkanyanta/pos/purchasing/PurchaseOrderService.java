package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.purchasing.PurchasingDtos.OrderLineRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.OrderLineResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.devkanyanta.pos.sale.SaleNumberGenerator;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.shared.web.PageResponse;
import com.devkanyanta.pos.supplier.SupplierEntity;
import com.devkanyanta.pos.supplier.SupplierService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** Purchase order lifecycle. Ordering never touches stock; see {@link GoodsReceiptService}. */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final Map<String, String> SORTS = Map.of(
            "poNumber", "poNumber", "orderDate", "orderDate", "expectedDate", "expectedDate",
            "createdAt", "createdAt", "status", "status");

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final SupplierService supplierService;

    private final ProductRepository productRepository;

    private final SaleNumberGenerator numberGenerator;

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request) {

        if (request == null || request.supplierId() == null) {
            throw new BadRequestException("supplierId is required");
        }
        SupplierEntity supplier = activeSupplier(request.supplierId());

        PurchaseOrderEntity order = PurchaseOrderEntity.builder()
                .poNumber(numberGenerator.generatePurchaseOrderNumber())
                .supplier(supplier)
                .createdBy(CurrentUser.username())
                .orderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now())
                .build();
        applyDetails(order, request);
        replaceLines(order, request.lines());

        if (Boolean.TRUE.equals(request.order())) {
            place(order);
        }
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderRequest request) {

        PurchaseOrderEntity order = lock(id);
        if (order.getStatus() != PurchaseOrderEntity.Status.DRAFT) {
            throw new ConflictException("Only draft orders can be edited; this one is " + order.getStatus());
        }
        if (request == null) {
            throw new BadRequestException("Purchase order request is required");
        }
        if (request.supplierId() != null) {
            order.setSupplier(activeSupplier(request.supplierId()));
        }
        if (request.orderDate() != null) {
            order.setOrderDate(request.orderDate());
        }
        applyDetails(order, request);
        if (request.lines() != null) {
            replaceLines(order, request.lines());
        }
        return toResponse(purchaseOrderRepository.save(order));
    }

    /** DRAFT -> ORDERED. */
    @Transactional
    public PurchaseOrderResponse place(Long id) {
        PurchaseOrderEntity order = lock(id);
        if (order.getStatus() != PurchaseOrderEntity.Status.DRAFT) {
            throw new ConflictException("Only draft orders can be placed; this one is " + order.getStatus());
        }
        place(order);
        return toResponse(purchaseOrderRepository.save(order));
    }

    /** Cancels an order nothing has been received for. */
    @Transactional
    public PurchaseOrderResponse cancel(Long id, String reason) {
        PurchaseOrderEntity order = lock(id);
        if (order.getStatus() != PurchaseOrderEntity.Status.DRAFT
                && order.getStatus() != PurchaseOrderEntity.Status.ORDERED) {
            throw new ConflictException("Cannot cancel an order that is " + order.getStatus());
        }
        if (order.hasReceipts()) {
            throw new ConflictException("Goods were already received against this order; close it instead");
        }
        order.setStatus(PurchaseOrderEntity.Status.CANCELLED);
        order.setCancelledBy(CurrentUser.username());
        order.setCancelledAt(java.time.LocalDateTime.now());
        order.setCancelReason(Texts.truncate(Texts.clean(reason), 500));
        return toResponse(purchaseOrderRepository.save(order));
    }

    /** Accepts a short delivery: the outstanding quantities will never arrive. */
    @Transactional
    public PurchaseOrderResponse close(Long id, String reason) {
        PurchaseOrderEntity order = lock(id);
        if (order.getStatus() != PurchaseOrderEntity.Status.PARTIALLY_RECEIVED) {
            throw new ConflictException("Only partially received orders can be closed; this one is " + order.getStatus());
        }
        order.setStatus(PurchaseOrderEntity.Status.CLOSED);
        order.setCancelledBy(CurrentUser.username());
        order.setCancelledAt(java.time.LocalDateTime.now());
        order.setCancelReason(Texts.truncate(Texts.clean(reason), 500));
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        return toResponse(purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(String status, Long supplierId, LocalDate from, LocalDate to,
                                                      String q, Integer page, Integer size, String sort) {
        PurchaseOrderEntity.Status parsed = null;
        if (Texts.clean(status) != null) {
            try {
                parsed = PurchaseOrderEntity.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown status '" + status + "'");
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        String like = Texts.clean(q) == null ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        PurchaseOrderEntity.Status wanted = parsed;
        Specification<PurchaseOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (wanted != null) {
                all.add(cb.equal(root.get("status"), wanted));
            }
            if (supplierId != null) {
                all.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("orderDate"), from));
            }
            if (to != null) {
                all.add(cb.lessThanOrEqualTo(root.<LocalDate>get("orderDate"), to));
            }
            if (like != null) {
                all.add(cb.or(cb.like(cb.lower(root.get("poNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("supplierReference"), "")), like)));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        return PageResponse.of(
                purchaseOrderRepository.findAll(spec,
                        PageResponse.pageable(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "id"))),
                this::toResponse);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void place(PurchaseOrderEntity order) {
        if (order.getLines().isEmpty()) {
            throw new BadRequestException("An order needs at least one line before it can be placed");
        }
        order.setStatus(PurchaseOrderEntity.Status.ORDERED);
        order.setOrderedBy(CurrentUser.username());
        order.setOrderedAt(java.time.LocalDateTime.now());
    }

    private PurchaseOrderEntity lock(Long id) {
        return purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Purchase order not found"));
    }

    private SupplierEntity activeSupplier(Long supplierId) {
        SupplierEntity supplier = supplierService.find(supplierId);
        if (Boolean.FALSE.equals(supplier.getActive())) {
            throw new BadRequestException("Supplier is inactive: " + supplier.getName());
        }
        return supplier;
    }

    private void applyDetails(PurchaseOrderEntity order, PurchaseOrderRequest request) {
        if (request.expectedDate() != null) {
            if (request.expectedDate().isBefore(order.getOrderDate())) {
                throw new BadRequestException("Expected date cannot be before the order date");
            }
            order.setExpectedDate(request.expectedDate());
        }
        if (request.supplierReference() != null) {
            order.setSupplierReference(Texts.truncate(Texts.clean(request.supplierReference()), 100));
        }
        if (request.notes() != null) {
            order.setNotes(Texts.truncate(Texts.clean(request.notes()), 1000));
        }
    }

    private void replaceLines(PurchaseOrderEntity order, List<OrderLineRequest> requested) {

        if (requested == null || requested.isEmpty()) {
            if (order.getLines().isEmpty() && order.getId() == null) {
                throw new BadRequestException("A purchase order needs at least one line");
            }
            if (requested != null) {
                throw new BadRequestException("A purchase order needs at least one line");
            }
            return;
        }

        // Edit lines in place: removing and re-adding a product in one flush would hit the unique
        // (order, product) constraint, and keeping the rows keeps their ids stable for clients.
        Map<Long, PurchaseOrderLineEntity> existing = new java.util.HashMap<>();
        order.getLines().forEach(l -> existing.put(l.getProduct().getId(), l));
        Set<Long> keep = new HashSet<>();
        Set<Long> seen = new HashSet<>();

        for (OrderLineRequest line : requested) {
            if (line == null || line.productId() == null) {
                throw new BadRequestException("Each line needs a productId");
            }
            if (line.quantity() == null || line.quantity() <= 0) {
                throw new BadRequestException("Ordered quantity must be positive");
            }
            if (!seen.add(line.productId())) {
                throw new BadRequestException("Product " + line.productId() + " appears on more than one line");
            }
            ProductEntity product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + line.productId()));
            if (Boolean.FALSE.equals(product.getActive())) {
                throw new BadRequestException("Product is archived and cannot be ordered: " + product.getName());
            }
            if (!Boolean.TRUE.equals(product.getTrackInventory())) {
                throw new BadRequestException("Stock is not tracked for product: " + product.getName());
            }
            BigDecimal cost = line.unitCost() != null ? line.unitCost()
                    : (product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO);
            if (cost.signum() < 0) {
                throw new BadRequestException("Unit cost cannot be negative");
            }
            keep.add(product.getId());
            PurchaseOrderLineEntity current = existing.get(product.getId());
            if (current != null) {
                current.setOrderedQuantity(line.quantity());
                current.setUnitCost(cost.setScale(4, RoundingMode.HALF_UP));
            } else {
                order.getLines().add(PurchaseOrderLineEntity.builder()
                        .purchaseOrder(order)
                        .product(product)
                        .orderedQuantity(line.quantity())
                        .unitCost(cost.setScale(4, RoundingMode.HALF_UP))
                        .build());
            }
        }
        order.getLines().removeIf(l -> !keep.contains(l.getProduct().getId()));
    }

    PurchaseOrderResponse toResponse(PurchaseOrderEntity o) {
        List<OrderLineResponse> lines = o.getLines().stream().map(l -> new OrderLineResponse(
                l.getId(), l.getProduct().getId(), l.getProduct().getName(), l.getProduct().getSku(),
                l.getProduct().getBarcode(), l.getOrderedQuantity(), l.getReceivedQuantity(),
                l.getOutstandingQuantity(), l.getReturnedQuantity(), l.getUnitCost(), l.getOrderedCost())).toList();
        return new PurchaseOrderResponse(o.getId(), o.getPoNumber(), o.getSupplier().getId(), o.getSupplier().getName(),
                o.getStatus().name(), o.getOrderDate(), o.getExpectedDate(), o.getSupplierReference(), o.getNotes(),
                o.getTotalCost(), o.getCreatedBy(), o.getOrderedBy(), o.getOrderedAt(), o.getCancelledBy(),
                o.getCancelledAt(), o.getCancelReason(), o.getCreatedAt(), lines);
    }
}
