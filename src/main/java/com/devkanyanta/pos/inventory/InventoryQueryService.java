package com.devkanyanta.pos.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.inventory.InventoryDtos.StockLevel;
import com.devkanyanta.pos.inventory.InventoryDtos.StockSummary;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.dto.InventoryMovementResponse;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.product.ProductSpecifications;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.shared.web.PageResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** Current stock positions and the paginated movement history. */
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private static final Map<String, String> STOCK_SORTS = Map.of(
            "name", "name", "sku", "sku", "stockQuantity", "stockQuantity", "averageCost", "averageCost",
            "price", "price", "updatedAt", "updatedAt");

    private static final Map<String, String> MOVEMENT_SORTS = Map.of("createdAt", "createdAt", "quantity", "quantity");

    private final ProductRepository productRepository;

    private final InventoryMovementRepository movementRepository;

    private final PosProperties properties;

    private final EntityManager entityManager;

    /** Stock positions of inventory-tracked products. {@code status}: IN_STOCK, LOW_STOCK, OUT_OF_STOCK. */
    @Transactional(readOnly = true)
    public PageResponse<StockLevel> stockLevels(String q, Long categoryId, String status, Boolean active,
                                                Integer page, Integer size, String sort) {

        var pageable = PageResponse.pageable(page, size, sort, STOCK_SORTS, Sort.by("name").and(Sort.by("id")));
        return PageResponse.of(productRepository.findAll(stockSpec(q, categoryId, status, active), pageable), this::toLevel);
    }

    @Transactional(readOnly = true)
    public StockSummary summary(Long categoryId) {

        long tracked = count(categoryId, null);
        long out = count(categoryId, "OUT_OF_STOCK");
        long low = count(categoryId, "LOW_STOCK");

        List<Object[]> rows = entityManager.createQuery("""
                SELECT COALESCE(SUM(p.stockQuantity * COALESCE(p.averageCost, 0)), 0),
                       COALESCE(SUM(p.stockQuantity * p.price), 0)
                FROM ProductEntity p
                WHERE p.trackInventory = true AND p.stockQuantity > 0
                  AND (:categoryId IS NULL OR p.category.id = :categoryId)
                """, Object[].class).setParameter("categoryId", categoryId).getResultList();
        BigDecimal cost = money(rows.get(0)[0]);
        BigDecimal retail = money(rows.get(0)[1]);
        return new StockSummary(tracked, tracked - out - low, low, out, cost, retail);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> movements(Long productId, String type, String reason,
                                                             String referenceType, String performedBy,
                                                             LocalDate from, LocalDate to,
                                                             Integer page, Integer size, String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        MovementType parsed = null;
        if (Texts.clean(type) != null) {
            try {
                parsed = MovementType.valueOf(type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown movement type '" + type + "'");
            }
        }
        MovementType movementType = parsed;
        String reasonCode = Texts.clean(reason) == null ? null : reason.trim().toUpperCase(Locale.ROOT);
        String refType = Texts.clean(referenceType) == null ? null : referenceType.trim().toUpperCase(Locale.ROOT);
        String user = Texts.clean(performedBy);

        Specification<InventoryMovementEntity> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (productId != null) {
                all.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (movementType != null) {
                all.add(cb.equal(root.get("movementType"), movementType));
            }
            if (reasonCode != null) {
                all.add(cb.equal(root.get("reason"), reasonCode));
            }
            if (refType != null) {
                all.add(cb.equal(root.get("referenceType"), refType));
            }
            if (user != null) {
                all.add(cb.equal(root.get("performedBy"), user));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };

        var pageable = PageResponse.pageable(page, size, sort, MOVEMENT_SORTS, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(movementRepository.findAll(spec, pageable), this::toMovement);
    }

    // ---- helpers ----

    private long count(Long categoryId, String status) {
        return productRepository.count(stockSpec(null, categoryId, status, null));
    }

    private Specification<ProductEntity> stockSpec(String q, Long categoryId, String status, Boolean active) {

        String wanted = Texts.clean(status) == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !List.of("IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK").contains(wanted)) {
            throw new BadRequestException("status must be IN_STOCK, LOW_STOCK or OUT_OF_STOCK");
        }
        int defaultThreshold = properties.getLowStockThreshold();

        Specification<ProductEntity> base = ProductSpecifications.matching(
                new ProductSpecifications.Filter(q, categoryId, null, active, true, null, null, null, null, null),
                defaultThreshold);

        return base.and((root, query, cb) -> {
            if (wanted == null) {
                return null;
            }
            var stock = root.<Integer>get("stockQuantity");
            var threshold = cb.coalesce(root.<Integer>get("reorderLevel"), defaultThreshold);
            return switch (wanted) {
                case "OUT_OF_STOCK" -> cb.lessThanOrEqualTo(stock, 0);
                case "LOW_STOCK" -> cb.and(cb.greaterThan(stock, 0), cb.lessThanOrEqualTo(stock, threshold));
                default -> cb.greaterThan(stock, threshold);
            };
        });
    }

    private StockLevel toLevel(ProductEntity p) {
        int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
        int threshold = p.getReorderLevel() != null ? p.getReorderLevel() : properties.getLowStockThreshold();
        String status = stock <= 0 ? "OUT_OF_STOCK" : stock <= threshold ? "LOW_STOCK" : "IN_STOCK";
        BigDecimal avg = p.getAverageCost() != null ? p.getAverageCost() : BigDecimal.ZERO;
        int sellable = Math.max(stock, 0);
        return new StockLevel(p.getId(), p.getName(), p.getSku(), p.getBarcode(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getUnit() != null ? p.getUnit().getCode() : null,
                !Boolean.FALSE.equals(p.getActive()), stock, threshold, status, stock < 0, avg,
                avg.multiply(BigDecimal.valueOf(sellable)).setScale(2, RoundingMode.HALF_UP),
                p.getPrice(), p.getPrice().multiply(BigDecimal.valueOf(sellable)).setScale(2, RoundingMode.HALF_UP));
    }

    private InventoryMovementResponse toMovement(InventoryMovementEntity m) {
        return InventoryMovementResponse.builder()
                .id(m.getId()).productId(m.getProduct().getId()).productName(m.getProduct().getName())
                .movementType(m.getMovementType().name()).quantity(m.getQuantity())
                .referenceId(m.getReferenceId()).referenceType(m.getReferenceType()).notes(m.getNotes())
                .reason(m.getReason()).unitCost(m.getUnitCost()).value(m.getValue())
                .balanceAfter(m.getBalanceAfter()).requestId(m.getRequestId())
                .performedBy(m.getPerformedBy()).createdAt(m.getCreatedAt())
                .build();
    }

    static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal decimal = value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}
