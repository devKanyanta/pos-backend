package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.customer.CustomerEntity;
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

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SaleStatus saleStatus = SaleStatus.ACTIVE;

    private String notes;

    @Column(nullable = false)
    private String createdBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItemEntity> items = new ArrayList<>();

    public enum PaymentStatus {
        PENDING, PAID, PARTIALLY_PAID, REFUNDED
    }

    public enum SaleStatus {
        ACTIVE, VOIDED, REFUNDED
    }
}
