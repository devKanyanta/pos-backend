package com.devkanyanta.pos.product.mapper;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.dto.ProductResponse;

@Component
public class ProductMapper {

    public ProductResponse toResponse(ProductEntity product) {

        CategoryEntity category = product.getCategory();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .sku(product.getSku())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .build();
    }
}
