package com.devkanyanta.pos.product;

import java.util.List;

import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;
import com.devkanyanta.pos.shared.web.PageResponse;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    /** Finds the product (active or not) carrying this barcode; 404 when none does. */
    ProductResponse getProductByBarcode(String barcode);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);

    List<ProductResponse> getLowStockProducts();

    /** Filtered, sorted, paginated listing. */
    PageResponse<ProductResponse> search(ProductSpecifications.Filter filter, Integer page, Integer size, String sort);

    /** Takes a product out of sale (and out of new purchase orders) without touching its history. */
    ProductResponse archiveProduct(Long id);

    ProductResponse restoreProduct(Long id);

    List<PriceChangeResponse> getPriceHistory(Long id);

    record PriceChangeResponse(Long id, java.math.BigDecimal oldPrice, java.math.BigDecimal newPrice,
                               java.math.BigDecimal oldCostPrice, java.math.BigDecimal newCostPrice,
                               String changedBy, java.time.LocalDateTime changedAt) {
    }
}
