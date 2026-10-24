package com.devkanyanta.pos.product.mapper;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.shared.config.PosProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final PosProperties properties;

    public ProductResponse toResponse(ProductEntity product) {

        CategoryEntity category = product.getCategory();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .unitId(product.getUnit() != null ? product.getUnit().getId() : null)
                .unitCode(product.getUnit() != null ? product.getUnit().getCode() : null)
                .barcode(product.getBarcode())
                .sku(product.getSku())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .averageCost(product.getAverageCost())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .vatRate(product.getVatRate())
                .trackInventory(product.getTrackInventory())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .reorderLevel(product.getReorderLevel())
                .lowStock(isLowStock(product))
                .zraTaxType(product.getZraTaxType() != null ? product.getZraTaxType().getCode() : null)
                .zraItemCode(product.getZraItemCode())
                .zraItemClassCode(product.getZraItemClassCode())
                .zraItemTypeCode(product.getZraItemTypeCode())
                .zraOriginCountryCode(product.getZraOriginCountryCode())
                .zraPackagingUnitCode(product.getZraPackagingUnitCode())
                .zraQuantityUnitCode(product.getZraQuantityUnitCode())
                .zraMapped(product.isZraMapped())
                .zraRegistered(Boolean.TRUE.equals(product.getZraRegistered()))
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public boolean isLowStock(ProductEntity product) {

        if (!Boolean.TRUE.equals(product.getTrackInventory()) || Boolean.FALSE.equals(product.getActive())) {
            return false;
        }

        int threshold = product.getReorderLevel() != null
                ? product.getReorderLevel()
                : properties.getLowStockThreshold();

        return product.getStockQuantity() != null && product.getStockQuantity() <= threshold;
    }
}
