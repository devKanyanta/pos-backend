package com.devkanyanta.pos.purchasing;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, Long>, JpaSpecificationExecutor<PurchaseOrderEntity> {

    /** Locks the order so concurrent receipts and edits are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrderEntity p WHERE p.id = :id")
    Optional<PurchaseOrderEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySupplierId(Long supplierId);


}
