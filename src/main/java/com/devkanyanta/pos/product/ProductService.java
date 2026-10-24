package com.devkanyanta.pos.product;

import java.util.List;

import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    /** Finds the product (active or not) carrying this barcode; 404 when none does. */
    ProductResponse getProductByBarcode(String barcode);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);

    List<ProductResponse> getLowStockProducts();
}
