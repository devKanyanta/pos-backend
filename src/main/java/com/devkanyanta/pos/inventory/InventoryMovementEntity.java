package com.devkanyanta.pos.inventory;

import java.time.LocalDateTime;

import com.devkanyanta.pos.product.ProductEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

    @Column(nullable = false)
    private Integer quantity;

    private Long referenceId;

    private String referenceType;

    private String notes;

    @Column(nullable = false)
    private String performedBy;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum MovementType {
        SALE, RESTOCK, ADJUSTMENT, RETURN, TRANSFER
    }
}
