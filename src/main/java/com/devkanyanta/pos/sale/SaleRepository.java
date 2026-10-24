package com.devkanyanta.pos.sale;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

    Optional<SaleEntity> findBySaleNumber(String saleNumber);

    Optional<SaleEntity> findByClientSaleId(String clientSaleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaleEntity s WHERE s.id = :id")
    Optional<SaleEntity> findByIdForUpdate(@Param("id") Long id);

    List<SaleEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end
    );

    /** Sales by business time (device time for offline sales), newest first. */
    @Query("SELECT s FROM SaleEntity s WHERE s.soldAt >= :start AND s.soldAt < :end ORDER BY s.soldAt DESC")
    List<SaleEntity> findSoldBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<SaleEntity> findByNeedsReviewTrueOrderBySoldAtDesc();

    long countByNeedsReviewTrue();

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM SaleEntity s " +
           "WHERE s.soldAt >= :start AND s.soldAt < :end AND s.saleStatus <> 'VOIDED'")
    java.math.BigDecimal totalRevenueBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(s.refundedAmount), 0) FROM SaleEntity s " +
           "WHERE s.soldAt >= :start AND s.soldAt < :end AND s.saleStatus <> 'VOIDED'")
    java.math.BigDecimal totalRefundedForSalesBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(s.vatAmount), 0) FROM SaleEntity s " +
           "WHERE s.soldAt >= :start AND s.soldAt < :end AND s.saleStatus <> 'VOIDED'")
    java.math.BigDecimal totalVatBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(s) FROM SaleEntity s " +
           "WHERE s.soldAt >= :start AND s.soldAt < :end AND s.saleStatus <> 'VOIDED'")
    Long countSalesBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    boolean existsByCustomerId(Long customerId);
}
