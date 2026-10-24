package com.devkanyanta.pos.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/** Filters for the product search endpoint. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** All filters are optional; null means "do not filter on this". */
    public record Filter(
            String q,
            Long categoryId,
            Long unitId,
            Boolean active,
            Boolean trackInventory,
            Boolean lowStock,
            Boolean outOfStock,
            Boolean hasBarcode,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
    }

    public static Specification<ProductEntity> matching(Filter f, int defaultLowStockThreshold) {

        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();

            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("sku"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("barcode"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like, '\\')));
            }
            if (f.categoryId() != null) {
                all.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.unitId() != null) {
                all.add(cb.equal(root.get("unit").get("id"), f.unitId()));
            }
            if (f.active() != null) {
                // Legacy rows may have a null flag, which has always meant active.
                Predicate isActive = cb.or(cb.isNull(root.get("active")), cb.isTrue(root.get("active")));
                all.add(f.active() ? isActive : cb.not(isActive));
            }
            if (f.trackInventory() != null) {
                all.add(f.trackInventory() ? cb.isTrue(root.get("trackInventory")) : cb.not(cb.isTrue(root.get("trackInventory"))));
            }
            if (f.hasBarcode() != null) {
                all.add(f.hasBarcode() ? cb.isNotNull(root.get("barcode")) : cb.isNull(root.get("barcode")));
            }
            if (Boolean.TRUE.equals(f.outOfStock())) {
                all.add(cb.isTrue(root.get("trackInventory")));
                all.add(cb.lessThanOrEqualTo(root.<Integer>get("stockQuantity"), 0));
            }
            if (Boolean.TRUE.equals(f.lowStock())) {
                all.add(cb.isTrue(root.get("trackInventory")));
                all.add(cb.or(cb.isNull(root.get("active")), cb.isTrue(root.get("active"))));
                all.add(cb.lessThanOrEqualTo(root.<Integer>get("stockQuantity"),
                        cb.coalesce(root.<Integer>get("reorderLevel"), defaultLowStockThreshold)));
            }
            if (f.minPrice() != null) {
                all.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), f.minPrice()));
            }
            if (f.maxPrice() != null) {
                all.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), f.maxPrice()));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }
}
