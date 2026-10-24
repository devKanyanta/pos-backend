package com.devkanyanta.pos.supplier;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String contactPerson;

    @Column(length = 30)
    private String phone;

    private String email;

    @Column(length = 500)
    private String address;

    /** Supplier's ZRA taxpayer id. */
    @Column(length = 10)
    private String tpin;

    @Column(length = 1000)
    private String notes;

    /** Inactive suppliers cannot receive new purchase orders; their history is kept. */
    @Builder.Default
    private Boolean active = true;
}
