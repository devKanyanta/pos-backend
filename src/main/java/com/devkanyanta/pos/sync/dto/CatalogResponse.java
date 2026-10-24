package com.devkanyanta.pos.sync.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.devkanyanta.pos.payment.dto.PaymentMethodResponse;

import lombok.Builder;
import lombok.Getter;

/**
 * Catalogue snapshot (or delta) for the device's local database. Pass {@code serverTime} back as
 * {@code since} on the next call. Products missing from {@link #productIds} were deleted on the
 * server and should be removed locally.
 */
@Getter
@Builder
public class CatalogResponse {

    private LocalDateTime serverTime;

    /** True when this is a full snapshot (no or too old 'since'). */
    private boolean full;

    private List<ProductItem> products;

    private List<CategoryItem> categories;

    private List<PaymentMethodResponse> paymentMethods;

    /** Every product id currently on the server. */
    private List<Long> productIds;

    @Getter
    @Builder
    public static class ProductItem {

        private Long id;

        private String name;

        private String sku;

        private String barcode;

        private BigDecimal price;

        private BigDecimal vatRate;

        private String zraTaxType;

        private Boolean trackInventory;

        private Integer stockQuantity;

        private Boolean active;

        private Long categoryId;

        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class CategoryItem {

        private Long id;

        private String name;
    }
}
