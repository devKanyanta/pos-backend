package com.devkanyanta.pos.product;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductRepository
        extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByBarcode(String barcode);

    /** Barcode lookup (served by the unique index on barcode); several values cover UPC-A/EAN-13 forms. */
    List<ProductEntity> findByBarcodeIn(Collection<String> barcodes);

    Optional<ProductEntity> findBySku(String sku);

    Optional<ProductEntity> findByZraItemCode(String zraItemCode);

    boolean existsByCategoryId(Long categoryId);

    /** Locks the product row so concurrent sales cannot lose stock updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM ProductEntity p WHERE p.trackInventory = true AND (p.active IS NULL OR p.active = true) "
            + "AND p.stockQuantity <= COALESCE(p.reorderLevel, :defaultThreshold) ORDER BY p.stockQuantity ASC, p.name ASC")
    List<ProductEntity> findLowStock(@Param("defaultThreshold") int defaultThreshold);

    List<ProductEntity> findByUpdatedAtAfterOrderByIdAsc(LocalDateTime since);

    @Query("SELECT p.id FROM ProductEntity p ORDER BY p.id")
    List<Long> findAllIds();

    List<ProductEntity> findByZraRegisteredFalseOrZraRegisteredIsNull();
}
