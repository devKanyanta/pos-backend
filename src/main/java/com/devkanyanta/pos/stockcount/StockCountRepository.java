package com.devkanyanta.pos.stockcount;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StockCountRepository extends JpaRepository<StockCountEntity, Long>, JpaSpecificationExecutor<StockCountEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StockCountEntity c WHERE c.id = :id")
    Optional<StockCountEntity> findByIdForUpdate(@Param("id") Long id);



    boolean existsByLinesProductId(Long productId);
}
