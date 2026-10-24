package com.devkanyanta.pos.sale.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.sale.SaleEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One refund event against a sale. Each refund is reported to ZRA as a credit note.
 */
@Entity
@Table(name = "sale_refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRefundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Credit note number, format CRN-YYYYMMDD-XXXX. */
    @Column(nullable = false, unique = true, length = 40)
    private String refundNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleEntity sale;

    /** Refund value including VAT. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    private String reason;

    /** ZRA refund reason code (rfdRsnCd). */
    @Column(length = 4)
    private String zraReasonCode;

    @Column(nullable = false)
    private String createdBy;

    // ---- ZRA credit note verification data ---------------------------------------------

    private Long zraInvoiceNo;

    private Long zraReceiptNo;

    @Column(length = 200)
    private String zraInternalData;

    @Column(length = 200)
    private String zraReceiptSignature;

    private LocalDateTime zraSignedAt;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SaleRefundItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<SaleRefundPaymentEntity> payments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
