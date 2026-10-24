package com.devkanyanta.pos.merchant;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The single business (merchant) this installation serves. Holds the account id quoted to
 * support, the receipt header details and the subscription plan.
 */
@Entity
@Table(name = "merchant_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantProfileEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable public account identifier, e.g. POS-7K2M9QXA. */
    @Column(nullable = false, unique = true, length = 32)
    private String accountId;

    private String businessName;

    /** Taxpayer identification number (10 digits). */
    @Column(length = 10)
    private String tpin;

    private String phone;

    private String email;

    private String address;

    /** Optional footer printed at the bottom of receipts. */
    @Column(length = 500)
    private String receiptFooter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MerchantPlan plan = MerchantPlan.SME;
}
