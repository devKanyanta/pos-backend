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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
