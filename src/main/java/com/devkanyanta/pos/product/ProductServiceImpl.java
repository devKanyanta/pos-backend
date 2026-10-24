package com.devkanyanta.pos.product;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
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
import com.devkanyanta.pos.shared.web.PageResponse;
import com.devkanyanta.pos.auth.security.PermissionConstants;

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

    private final ProductPriceHistoryRepository priceHistoryRepository;

    private final com.devkanyanta.pos.stockcount.StockCountRepository stockCountRepository;

    private final com.devkanyanta.pos.purchasing.PurchaseOrderLineRepository purchaseOrderLineRepository;

    /** Public sort names for the search endpoint -> entity properties. */
    private static final java.util.Map<String, String> SORTS = java.util.Map.of(
            "name", "name", "sku", "sku", "price", "price", "costPrice", "costPrice",
            "stockQuantity", "stockQuantity", "createdAt", "createdAt", "updatedAt", "updatedAt");

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {

        ProductEntity product = productRules.buildNew(request);

        int openingStock = product.getStockQuantity();
        product.setStockQuantity(0);
        product = productRepository.save(product);

        if (Boolean.TRUE.equals(product.getTrackInventory()) && openingStock > 0) {
            stockLedger.record(StockLedger.Change.builder()
                    .product(product).type(MovementType.OPENING).quantity(openingStock)
                    .unitCost(product.getCostPrice() != null ? product.getCostPrice() : java.math.BigDecimal.ZERO)
                    .referenceId(product.getId()).referenceType("PRODUCT_CREATE")
                    .notes("Opening stock").performedBy(CurrentUser.username())
                    .build());
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
        java.math.BigDecimal priceBefore = product.getPrice();
        java.math.BigDecimal costBefore = product.getCostPrice();

        productRules.applyUpdate(product, request);

        boolean priceChanged = priceBefore != null && product.getPrice() != null
                && priceBefore.compareTo(product.getPrice()) != 0;
        boolean costChanged = !java.util.Objects.equals(costBefore, product.getCostPrice())
                && (costBefore == null || product.getCostPrice() == null
                || costBefore.compareTo(product.getCostPrice()) != 0);
        if ((priceChanged || costChanged) && !CurrentUser.hasAuthority(PermissionConstants.PRODUCT_PRICE_UPDATE)) {
            throw new AccessDeniedException("PRODUCT_PRICE_UPDATE permission is required to change prices or costs");
        }
        if (product.getStockQuantity() != null && product.getStockQuantity() != stockBefore
                && !CurrentUser.hasAuthority(PermissionConstants.INVENTORY_ADJUST)) {
            throw new AccessDeniedException("INVENTORY_ADJUST permission is required to change stock quantities");
        }
        if (priceChanged || costChanged) {
            priceHistoryRepository.save(ProductPriceHistoryEntity.builder()
                    .product(product)
                    .oldPrice(priceBefore).newPrice(product.getPrice())
                    .oldCostPrice(costBefore).newCostPrice(product.getCostPrice())
                    .changedBy(CurrentUser.username())
                    .build());
        }

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

        if (saleItemRepository.existsByProductId(id) || inventoryMovementRepository.existsByProductId(id)
                || purchaseOrderLineRepository.existsByProductId(id)
                || stockCountRepository.existsByLinesProductId(id)) {
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSpecifications.Filter filter, Integer page, Integer size, String sort) {

        var pageable = PageResponse.pageable(page, size, sort, SORTS, Sort.by("name").and(Sort.by("id")));
        return PageResponse.of(
                productRepository.findAll(ProductSpecifications.matching(filter, properties.getLowStockThreshold()), pageable),
                productMapper::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse archiveProduct(Long id) {
        ProductEntity product = findProductById(id);
        product.setActive(false);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse restoreProduct(Long id) {
        ProductEntity product = findProductById(id);
        product.setActive(true);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceChangeResponse> getPriceHistory(Long id) {
        findProductById(id);
        return priceHistoryRepository.findByProductIdOrderByChangedAtDescIdDesc(id).stream()
                .map(h -> new PriceChangeResponse(h.getId(), h.getOldPrice(), h.getNewPrice(),
                        h.getOldCostPrice(), h.getNewCostPrice(), h.getChangedBy(), h.getChangedAt()))
                .toList();
    }

    private ProductEntity findProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }
}
