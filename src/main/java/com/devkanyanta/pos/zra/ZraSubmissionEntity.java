package com.devkanyanta.pos.zra;

import java.time.LocalDateTime;

import com.devkanyanta.pos.receipt.ReceiptEntity;
import com.devkanyanta.pos.sale.SaleEntity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SubmissionStatus {
        PENDING, SUCCESS, FAILED, RETRYING
    }

    public enum SubmissionType {
        SALE_INVOICE, CREDIT_NOTE
    }
}
