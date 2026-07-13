package com.devkanyanta.pos.sale;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NumberSequence {

    @Id
    @Column(name = "seq_id")
    private String id;

    @Column(nullable = false)
    private Integer currentValue;
}
