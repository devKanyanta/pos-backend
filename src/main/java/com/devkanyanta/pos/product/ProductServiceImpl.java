package com.devkanyanta.pos.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.category.CategoryRepository;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;
import com.devkanyanta.pos.product.mapper.ProductMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    private final ProductMapper productMapper;

    @Override
    public ProductResponse createProduct(CreateProductRequest request) {

        validateCreateRequest(request);
        validateUniqueFields(request.getBarcode(), request.getSku(), null);

        ProductEntity product = ProductEntity.builder()
                .name(request.getName())
                .barcode(request.getBarcode())
                .sku(request.getSku())
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .stockQuantity(request.getStockQuantity())
                .active(Boolean.TRUE)
                .category(resolveCategory(request.getCategoryId()))
                .build();

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getProductById(Long id) {

        return productMapper.toResponse(findProductById(id));
    }

    @Override
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {

        ProductEntity product = findProductById(id);

        validateUpdateRequest(request);
        validateUniqueFields(request.getBarcode(), request.getSku(), id);

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getBarcode() != null) {
            product.setBarcode(request.getBarcode());
        }
        if (request.getSku() != null) {
            product.setSku(request.getSku());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        }

        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }

        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        if (request.getCategoryId() != null) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    public void deleteProduct(Long id) {

        productRepository.delete(findProductById(id));
    }

    private ProductEntity findProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private CategoryEntity resolveCategory(Long categoryId) {

        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    private void validateUniqueFields(String barcode, String sku, Long productId) {

        if (barcode != null) {
            productRepository.findByBarcode(barcode)
                    .filter(existingProduct -> !existingProduct.getId().equals(productId))
                    .ifPresent(existingProduct -> {
                        throw new RuntimeException("Product barcode already exists");
                    });
        }

        if (sku != null) {
            productRepository.findBySku(sku)
                    .filter(existingProduct -> !existingProduct.getId().equals(productId))
                    .ifPresent(existingProduct -> {
                        throw new RuntimeException("Product SKU already exists");
                    });
        }
    }

    private void validateCreateRequest(CreateProductRequest request) {

        if (request == null) {
            throw new RuntimeException("Product request is required");
        }

        validateName(request.getName());
        validatePrice(request.getPrice());
        validateCostPrice(request.getCostPrice());
        validateStockQuantity(request.getStockQuantity());
    }

    private void validateUpdateRequest(UpdateProductRequest request) {

        if (request == null) {
            throw new RuntimeException("Product request is required");
        }

        if (request.getName() != null) {
            validateName(request.getName());
        }
        if (request.getPrice() != null) {
            validatePrice(request.getPrice());
        }
        if (request.getCostPrice() != null) {
            validateCostPrice(request.getCostPrice());
        }
        if (request.getStockQuantity() != null) {
            validateStockQuantity(request.getStockQuantity());
        }
    }

    private void validateName(String name) {

        if (!StringUtils.hasText(name)) {
            throw new RuntimeException("Product name is required");
        }
    }

    private void validatePrice(java.math.BigDecimal price) {

        if (price == null) {
            throw new RuntimeException("Product price is required");
        }

        if (price.signum() < 0) {
            throw new RuntimeException("Product price cannot be negative");
        }
    }

    private void validateCostPrice(java.math.BigDecimal costPrice) {

        if (costPrice != null && costPrice.signum() < 0) {
            throw new RuntimeException("Product cost price cannot be negative");
        }
    }

    private void validateStockQuantity(Integer stockQuantity) {

        if (stockQuantity == null) {
            throw new RuntimeException("Product stock quantity is required");
        }

        if (stockQuantity < 0) {
            throw new RuntimeException("Product stock quantity cannot be negative");
        }
    }
}
