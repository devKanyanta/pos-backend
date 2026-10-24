package com.devkanyanta.pos.zra;

import java.time.LocalDateTime;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The VSDC device this installation reports through, as returned by device initialisation
 * (initializer/selectInitInfo). Holds the running invoice number sequence.
 */
@Entity
@Table(name = "zra_device")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZraDeviceEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String tpin;

    /** Branch id, "000" for the head office. */
    @Column(nullable = false, length = 3)
    private String bhfId;

    @Column(nullable = false, length = 100)
    private String deviceSerialNo;

    @Column(length = 40)
    private String sdcId;

    @Column(length = 40)
    private String mrcNo;

    private String taxpayerName;

    private String branchName;

    /** Last invoice number used (orgInvcNo sequence). */
    @Builder.Default
    private Long lastInvoiceNo = 0L;

    private LocalDateTime initializedAt;

    @Column(columnDefinition = "TEXT")
    private String initResponse;

    @Builder.Default
    private Boolean active = true;
}
