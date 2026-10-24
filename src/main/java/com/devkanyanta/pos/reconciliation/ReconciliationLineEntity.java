package com.devkanyanta.pos.reconciliation;

import java.math.BigDecimal;

import com.devkanyanta.pos.payment.PaymentMethodEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reconciliation_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private ReconciliationEntity reconciliation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    @Column(nullable = false)
    private Integer transactionCount;

    /** Received minus refunds paid out (plus opening float for cash). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    /** Counted cash / statement total entered by the user. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal variance;

    @Builder.Default
    private Boolean flagged = false;
}
