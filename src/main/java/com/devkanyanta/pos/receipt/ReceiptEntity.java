package com.devkanyanta.pos.receipt;

import java.time.LocalDateTime;

import com.devkanyanta.pos.sale.SaleEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleEntity sale;

    @Column(nullable = false, unique = true)
    private String receiptNumber;

    @Builder.Default
    private Integer printedCount = 1;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // ---- ZRA Smart Invoice verification data (returned by the VSDC) ---------------------

    /** Invoice number sent to the VSDC (orgInvcNo / cisInvcNo). */
    private Long zraInvoiceNo;

    /** VSDC receipt number (rcptNo). */
    private Long zraReceiptNo;

    /** Internal data (intrlData) printed on the receipt. */
    @Column(length = 200)
    private String zraInternalData;

    /** Receipt signature (rcptSign) – the verification code printed on the receipt. */
    @Column(length = 200)
    private String zraReceiptSignature;

    @Column(length = 40)
    private String zraSdcId;

    @Column(length = 40)
    private String zraMrcNo;

    @Column(length = 500)
    private String zraQrCodeUrl;

    /** VSDC receipt publication date (vsdcRcptPbctDate). */
    @Column(length = 20)
    private String zraReceiptDate;

    private LocalDateTime zraSignedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isZraSigned() {
        return zraReceiptSignature != null && !zraReceiptSignature.isBlank();
    }
}
