package com.devkanyanta.pos.sale.refund;

import java.math.BigDecimal;

import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.payment.PaymentMethodEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * How the money of a refund went back to the customer: which tender, and which original
 * payment (if any) it reverses. Used by the end-of-day reconciliation.
 */
@Entity
@Table(name = "sale_refund_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRefundPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private SaleRefundEntity refund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentEntity payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}
