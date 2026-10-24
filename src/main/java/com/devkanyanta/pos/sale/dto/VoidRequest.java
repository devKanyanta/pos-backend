package com.devkanyanta.pos.sale.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoidRequest {

    /** Why the sale is being voided; recorded with who did it and when. */
    private String reason;
}
