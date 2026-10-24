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
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReturnLineRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReturnLineResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.SupplierReturnRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.SupplierReturnResponse;
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

/**
 * Sending goods back to a supplier. It removes stock through the ledger (SUPPLIER_RETURN movements at the
 * product's average cost, never below zero) and leaves the original order and receipts untouched, so the
 * purchasing history stays intact. Idempotent on {@code requestId}.
 */
@Service
@RequiredArgsConstructor
public class SupplierReturnService {

    private static final Map<String, String> SORTS = Map.of("returnedAt", "returnedAt", "returnNumber", "returnNumber");

    private final SupplierReturnRepository supplierReturnRepository;

    private final PurchaseOrderRepository purchaseOrderRepository;

    private final ProductRepository productRepository;

    private final SupplierService supplierService;

    private final StockLedger stockLedger;

    private final SaleNumberGenerator numberGenerator;

    @Transactional
    public SupplierReturnResponse create(SupplierReturnRequest request) {

        if (request == null) {
            throw new BadRequestException("Supplier return request is required");
        }
        String requestId = Texts.clean(request.requestId());
        if (requestId == null || !requestId.matches(GoodsReceiptService.REQUEST_ID_PATTERN)) {
            throw new BadRequestException("requestId is required: 8-64 characters (letters, digits, . _ : -)");
        }
        String reason = Texts.clean(request.reason());
        if (reason == null) {
            throw new BadRequestException("A reason is required for supplier returns");
        }
        if (request.supplierId() == null) {
            throw new BadRequestException("supplierId is required");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("A return needs at least one line");
        }

        var replay = supplierReturnRepository.findByRequestId(requestId);
        if (replay.isPresent()) {
            return toResponse(replay.get());
        }

        SupplierEntity supplier = supplierService.find(request.supplierId());
        PurchaseOrderEntity order = null;
        if (request.purchaseOrderId() != null) {
            order = purchaseOrderRepository.findByIdForUpdate(request.purchaseOrderId())
                    .orElseThrow(() -> new NotFoundException("Purchase order not found"));
            if (!order.getSupplier().getId().equals(supplier.getId())) {
                throw new BadRequestException(order.getPoNumber() + " belongs to another supplier");
            }
        }

        String user = CurrentUser.username();
        SupplierReturnEntity ret = SupplierReturnEntity.builder()
                .returnNumber(numberGenerator.generateSupplierReturnNumber())
                .supplier(supplier)
                .purchaseOrder(order)
                .requestId(requestId)
                .reason(Texts.truncate(reason, 500))
                .returnedBy(user)
                .build();

        Map<Long, SupplierReturnLineEntity> byProduct = new TreeMap<>();
        Set<Long> seen = new HashSet<>();
        for (ReturnLineRequest line : request.lines()) {
            if (line == null || line.productId() == null) {
                throw new BadRequestException("Each return line needs a productId");
            }
            if (line.quantity() == null || line.quantity() <= 0) {
                throw new BadRequestException("Returned quantity must be positive");
            }
            if (!seen.add(line.productId())) {
                throw new BadRequestException("Product " + line.productId() + " appears on more than one line");
            }
            ProductEntity product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + line.productId()));

            PurchaseOrderLineEntity poLine = null;
            if (order != null) {
                poLine = order.getLines().stream()
                        .filter(l -> l.getProduct().getId().equals(product.getId())).findFirst()
                        .orElseThrow(() -> new BadRequestException("'" + product.getName() + "' is not on "
                                + request.purchaseOrderId() + "'s order"));
                int returnable = poLine.getReceivedQuantity() - poLine.getReturnedQuantity();
                if (line.quantity() > returnable) {
                    throw new BadRequestException("Cannot return " + line.quantity() + " of '" + product.getName()
                            + "': only " + returnable + " of the delivered goods can still be returned");
                }
            }
            BigDecimal credit = line.creditUnitCost() != null ? line.creditUnitCost()
                    : (poLine != null ? poLine.getUnitCost()
                    : (product.getAverageCost() != null ? product.getAverageCost() : BigDecimal.ZERO));
            if (credit.signum() < 0) {
                throw new BadRequestException("Credit unit cost cannot be negative");
            }
            byProduct.put(product.getId(), SupplierReturnLineEntity.builder()
                    .supplierReturn(ret).product(product).poLine(poLine).quantity(line.quantity())
                    .creditUnitCost(credit.setScale(4, RoundingMode.HALF_UP))
                    .build());
        }
        ret.getLines().addAll(byProduct.values());
        supplierReturnRepository.save(ret);

        for (SupplierReturnLineEntity line : ret.getLines()) {
            ProductEntity product = productRepository.findByIdForUpdate(line.getProduct().getId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            if (!Boolean.TRUE.equals(product.getTrackInventory())) {
                throw new BadRequestException("Stock is not tracked for product: " + product.getName());
            }
            var movement = stockLedger.record(StockLedger.Change.builder()
                    .product(product).type(MovementType.SUPPLIER_RETURN).quantity(-line.getQuantity())
                    .referenceId(ret.getId()).referenceType("SUPPLIER_RETURN")
                    .reason("SUPPLIER_RETURN")
                    .notes("Returned " + ret.getReturnNumber() + " to " + supplier.getName() + ": " + ret.getReason())
                    .performedBy(user)
                    .build());
            line.setStockUnitCost(movement.getUnitCost());
            if (line.getPoLine() != null) {
                line.getPoLine().setReturnedQuantity(line.getPoLine().getReturnedQuantity() + line.getQuantity());
            }
        }
        return toResponse(ret);
    }

    @Transactional(readOnly = true)
    public SupplierReturnResponse get(Long id) {
        return toResponse(supplierReturnRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier return not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierReturnResponse> search(Long supplierId, LocalDate from, LocalDate to,
                                                       Integer page, Integer size, String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        java.time.LocalDateTime start = from != null ? from.atStartOfDay() : null;
        java.time.LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : null;
        Specification<SupplierReturnEntity> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (supplierId != null) {
                all.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (start != null) {
                all.add(cb.greaterThanOrEqualTo(root.<java.time.LocalDateTime>get("returnedAt"), start));
            }
            if (end != null) {
                all.add(cb.lessThan(root.<java.time.LocalDateTime>get("returnedAt"), end));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
        return PageResponse.of(
                supplierReturnRepository.findAll(spec,
                        PageResponse.pageable(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "id"))),
                this::toResponse);
    }

    private SupplierReturnResponse toResponse(SupplierReturnEntity r) {
        List<ReturnLineResponse> lines = r.getLines().stream().map(l -> new ReturnLineResponse(
                l.getId(), l.getProduct().getId(), l.getProduct().getName(), l.getQuantity(),
                l.getCreditUnitCost(), l.getCreditTotal(), l.getStockUnitCost())).toList();
        return new SupplierReturnResponse(r.getId(), r.getReturnNumber(), r.getSupplier().getId(),
                r.getSupplier().getName(), r.getPurchaseOrder() != null ? r.getPurchaseOrder().getId() : null,
                r.getPurchaseOrder() != null ? r.getPurchaseOrder().getPoNumber() : null, r.getReason(),
                r.getReturnedBy(), r.getReturnedAt(), r.getCreditTotal(), lines);
    }
}
