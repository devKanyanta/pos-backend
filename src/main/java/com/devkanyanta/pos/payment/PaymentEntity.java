package com.devkanyanta.pos.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.devkanyanta.pos.sale.SaleEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleEntity sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    /** Amount applied to the sale (excludes change given). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Amount handed over by the customer (cash), when more than the amount due. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amountTendered;

    /** Change returned to the customer. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal changeAmount = BigDecimal.ZERO;

    /** Mobile money confirmation code, card approval code, etc. */
    private String transactionReference;

    /** Mobile money payer number, when captured. */
    @Column(length = 20)
    private String payerPhone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.SUCCESS;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** Username of the cashier who recorded the payment. */
    private String recordedBy;

    /** When the money was received (for offline sales: the time on the device). */
    private LocalDateTime paidAt;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (paidAt == null) {
            paidAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public BigDecimal getRefundableAmount() {
        BigDecimal refunded = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
        return amount.subtract(refunded);
    }

    public enum PaymentStatus {
        SUCCESS, FAILED, REFUNDED
    }
}
