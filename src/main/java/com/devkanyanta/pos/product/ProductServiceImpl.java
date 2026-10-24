package com.devkanyanta.pos.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.InventoryMovementRepository;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;
import com.devkanyanta.pos.product.mapper.ProductMapper;
import com.devkanyanta.pos.sale.SaleItemRepository;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Barcodes;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final ProductRules productRules;

    private final ProductMapper productMapper;

    private final StockLedger stockLedger;

    private final SaleItemRepository saleItemRepository;

    private final InventoryMovementRepository inventoryMovementRepository;

    private final PosProperties properties;

    private final ProductBarcodeLookup barcodeLookup;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {

        ProductEntity product = productRules.buildNew(request);

        int openingStock = product.getStockQuantity();
        product.setStockQuantity(0);
        product = productRepository.save(product);

        if (Boolean.TRUE.equals(product.getTrackInventory()) && openingStock > 0) {
            stockLedger.record(product, MovementType.RESTOCK, openingStock,
                    product.getId(), "PRODUCT_CREATE", "Opening stock", CurrentUser.username());
        } else {
            product.setStockQuantity(openingStock);
        }

        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        return productMapper.toResponse(findProductById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductByBarcode(String barcode) {

        if (Barcodes.canonical(barcode) == null) {
            throw new BadRequestException("Barcode is required");
        }

        return barcodeLookup.find(barcode)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("No product found for barcode " + Barcodes.canonical(barcode)));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {

        ProductEntity product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        int stockBefore = product.getStockQuantity() != null ? product.getStockQuantity() : 0;

        productRules.applyUpdate(product, request);

        int stockAfter = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int delta = stockAfter - stockBefore;

        if (delta != 0) {
            // Route the change through the ledger so it is recorded as an ADJUSTMENT movement.
            product.setStockQuantity(stockBefore);
            stockLedger.record(product, MovementType.ADJUSTMENT, delta,
                    product.getId(), "PRODUCT_UPDATE", "Stock set via product update", CurrentUser.username());
        } else {
            productRepository.save(product);
        }

        return productMapper.toResponse(product);
    }

    /**
     * Products that appear in sales or the stock audit trail are deactivated instead of deleted,
     * so historical records (and offline device caches) stay consistent.
     */
    @Override
    @Transactional
    public void deleteProduct(Long id) {

        ProductEntity product = findProductById(id);

        if (saleItemRepository.existsByProductId(id) || inventoryMovementRepository.existsByProductId(id)) {
            product.setActive(false);
            productRepository.save(product);
            return;
        }

        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {

        return productRepository.findLowStock(properties.getLowStockThreshold())
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    private ProductEntity findProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }
}
