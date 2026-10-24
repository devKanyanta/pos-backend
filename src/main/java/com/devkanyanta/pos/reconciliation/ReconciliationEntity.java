package com.devkanyanta.pos.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * End-of-day cash-up: what the system expects per tender versus what was counted / found on
 * the mobile money statements, with variances.
 */
@Entity
@Table(name = "reconciliations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate businessDate;

    /** Cash float in the drawer at the start of the day. */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredTotal;

    /** declared - expected (negative = short). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal varianceTotal;

    /** BALANCED, SHORT or OVER. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private String submittedBy;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ReconciliationLineEntity> lines = new ArrayList<>();
}
