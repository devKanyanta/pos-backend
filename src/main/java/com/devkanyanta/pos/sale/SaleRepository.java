package com.devkanyanta.pos.sale;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

    Optional<SaleEntity> findBySaleNumber(String saleNumber);

    List<SaleEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM SaleEntity s " +
           "WHERE s.createdAt BETWEEN :start AND :end AND s.saleStatus = 'ACTIVE'")
    java.math.BigDecimal totalRevenueBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(s.vatAmount), 0) FROM SaleEntity s " +
           "WHERE s.createdAt BETWEEN :start AND :end AND s.saleStatus = 'ACTIVE'")
    java.math.BigDecimal totalVatBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(s) FROM SaleEntity s " +
           "WHERE s.createdAt BETWEEN :start AND :end AND s.saleStatus = 'ACTIVE'")
    Long countSalesBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
