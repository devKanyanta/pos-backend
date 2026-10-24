package com.devkanyanta.pos.sale.refund;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRefundRepository extends JpaRepository<SaleRefundEntity, Long> {

    List<SaleRefundEntity> findBySaleIdOrderByIdAsc(Long saleId);

    List<SaleRefundEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT rp FROM SaleRefundPaymentEntity rp JOIN FETCH rp.paymentMethod JOIN FETCH rp.refund r "
            + "WHERE r.createdAt >= :start AND r.createdAt < :end")
    List<SaleRefundPaymentEntity> findRefundPaymentsBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
