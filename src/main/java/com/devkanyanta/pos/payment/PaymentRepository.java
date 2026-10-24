package com.devkanyanta.pos.payment;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findBySaleId(Long saleId);

    List<PaymentEntity> findBySaleIdOrderByIdAsc(Long saleId);

    @Query("SELECT COUNT(p) > 0 FROM PaymentEntity p WHERE p.paymentMethod.id = :methodId "
            + "AND UPPER(p.transactionReference) = UPPER(:reference) AND p.paymentStatus <> 'FAILED'")
    boolean existsActiveReference(@Param("methodId") Long methodId, @Param("reference") String reference);

    @Query("SELECT p FROM PaymentEntity p JOIN FETCH p.paymentMethod JOIN FETCH p.sale s "
            + "WHERE p.paidAt >= :start AND p.paidAt < :end AND p.paymentStatus <> 'FAILED' "
            + "AND s.saleStatus <> 'VOIDED'")
    List<PaymentEntity> findReceivedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    boolean existsByPaymentMethodId(Long paymentMethodId);
}
