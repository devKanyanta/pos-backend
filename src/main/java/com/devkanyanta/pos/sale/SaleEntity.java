package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.customer.CustomerEntity;
import com.devkanyanta.pos.payment.PaymentEntity;
import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String saleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    /** Sum of line totals (after line discounts, before the sale discount and VAT). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    /** Sale-level discount. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** Total value refunded through credit notes. */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SaleStatus saleStatus = SaleStatus.ACTIVE;

    private String notes;

    @Column(nullable = false)
    private String createdBy;

    /** When the sale actually happened (device time for offline sales). Used by reports. */
    private LocalDateTime soldAt;

    /** Unique id generated on the device; makes sale creation and offline sync idempotent. */
    @Column(unique = true, length = 64)
    private String clientSaleId;

    /** Device that captured the sale. */
    @Column(length = 64)
    private String deviceId;

    /** Receipt number printed by the device while offline, if any. */
    @Column(length = 64)
    private String clientReceiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private SaleSource source = SaleSource.ONLINE;

    /** Set when offline sync detected conflicts that a manager should review. */
    @Builder.Default
    private Boolean needsReview = false;

    /** Comma-separated conflict codes recorded during offline sync. */
    @Column(length = 500)
    private String syncConflicts;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SaleItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale")
    @OrderBy("id ASC")
    @Builder.Default
    private List<PaymentEntity> payments = new ArrayList<>();

    @PrePersist
    protected void onCreateSale() {
        if (soldAt == null) {
            soldAt = LocalDateTime.now();
        }
    }

    public enum PaymentStatus {
        PENDING, PAID, PARTIALLY_PAID, REFUNDED
    }

    public enum SaleStatus {
        ACTIVE, VOIDED, REFUNDED
    }

    public enum SaleSource {
        ONLINE, OFFLINE_SYNC
    }
}
