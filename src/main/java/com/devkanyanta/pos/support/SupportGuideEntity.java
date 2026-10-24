package com.devkanyanta.pos.support;

import com.devkanyanta.pos.shared.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Onboarding / troubleshooting content (setup guides, "how to issue a credit note" videos...)
 * delivered to merchants over WhatsApp.
 */
@Entity
@Table(name = "support_guides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportGuideEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Grouping such as SETUP, SALES, ZRA, RECONCILIATION, TROUBLESHOOTING. */
    @Column(nullable = false, length = 40)
    private String topic;

    @Column(length = 2000)
    private String description;

    /** Link to the video / PDF / article. */
    @Column(length = 500)
    private String mediaUrl;

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Boolean active = true;
}
