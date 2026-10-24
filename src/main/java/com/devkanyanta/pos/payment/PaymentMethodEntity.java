package com.devkanyanta.pos.payment;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Builder.Default
    private Boolean isActive = true;

    /** Tender category. Cash allows change; mobile money requires a reference. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethodType type;

    /** Whether a transaction reference (e.g. mobile money confirmation code) is mandatory. */
    private Boolean requiresReference;

    /** ZRA VSDC payment type code (pmtTyCd), e.g. 01 cash, 05 card, 06 mobile money. */
    @Column(length = 4)
    private String zraPaymentTypeCode;
}
