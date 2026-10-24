package com.devkanyanta.pos.product.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImportResult {

    private boolean dryRun;

    private int totalRows;

    private int created;

    private int updated;

    private int failed;

    private List<String> recognisedColumns;

    private List<String> ignoredColumns;

    private List<RowResult> rows;

    @Getter
    @Builder
    public static class RowResult {

        /** 1-based line number in the file (the header is line 1). */
        private int line;

        /** CREATED, UPDATED, WOULD_CREATE, WOULD_UPDATE or FAILED. */
        private String status;

        private Long productId;

        private String name;

        private String sku;

        private String barcode;

        private List<String> errors;
    }
}
