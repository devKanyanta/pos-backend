package com.devkanyanta.pos.stockcount;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A physical stock count. While OPEN it only collects counted quantities; committing it books the
 * differences to the ledger as COUNT movements.
 */
@Entity
@Table(name = "stock_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String countNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private String createdBy;

    private String committedBy;

    private LocalDateTime committedAt;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<StockCountLineEntity> lines = new ArrayList<>();

    public enum Status {
        OPEN, COMMITTED, CANCELLED
    }
}
