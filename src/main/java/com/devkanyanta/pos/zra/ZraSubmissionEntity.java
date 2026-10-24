package com.devkanyanta.pos.zra;

import java.time.LocalDateTime;

import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.sale.SaleEntity;
import com.devkanyanta.pos.sale.refund.SaleRefundEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Outbox record for one message to the ZRA VSDC (sales invoice, credit note or item
 * registration). Sales never wait for ZRA: they are queued here and transmitted by the
 * background processor with retries, so trading continues through ZRA or network outages.
 */
@Entity
@Table(name = "zra_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZraSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private SaleEntity sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private ReceiptEntity receipt;

    /** Set for credit notes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private SaleRefundEntity refund;

    /** Set for item registrations. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    /** Invoice number (orgInvcNo) allocated from the device sequence on first attempt. */
    private Long invoiceNo;

    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubmissionStatus submissionStatus = SubmissionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private SubmissionType submissionType;

    @Builder.Default
    private Integer retryCount = 0;

    private LocalDateTime lastAttemptAt;

    /** Earliest time the processor will (re)try this submission. */
    private LocalDateTime nextAttemptAt;

    private LocalDateTime completedAt;

    /** VSDC resultCd of the last attempt, or a local code (NETWORK, NOT_INITIALIZED...). */
    @Column(length = 40)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SubmissionStatus {
        PENDING, SUCCESS, FAILED, RETRYING
    }

    public enum SubmissionType {
        SALE_INVOICE, CREDIT_NOTE, ITEM_REGISTRATION
    }
}
