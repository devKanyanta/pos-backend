package com.devkanyanta.pos.whatsapp;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/** Audit log of WhatsApp messages sent through the provider API. */
@Entity
@Table(name = "whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String toNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(nullable = false, length = 20)
    private String provider;

    /** SENT or FAILED. */
    @Column(nullable = false, length = 20)
    private String status;

    private String providerMessageId;

    @Column(length = 1000)
    private String error;

    /** RECEIPT, Z_REPORT, SUPPORT_GUIDE ... */
    @Column(length = 40)
    private String context;

    private Long referenceId;

    private String sentBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
