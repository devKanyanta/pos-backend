package com.devkanyanta.pos.product;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.category.CategoryRepository;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.ConflictException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.util.Barcodes;
import com.devkanyanta.pos.shared.util.Money;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.unit.UnitEntity;
import com.devkanyanta.pos.unit.UnitRepository;
import com.devkanyanta.pos.zra.ZraTaxType;

import lombok.RequiredArgsConstructor;

/**
 * Validation and field mapping for products, shared by the product API and the CSV importer.
 * Methods only read from the database; they never write.
 */
@Component
@RequiredArgsConstructor
public class ProductRules {

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    private final PosProperties properties;

    private final ProductBarcodeLookup barcodeLookup;

    private final UnitRepository unitRepository;

    /** Builds a new (unsaved) product from a create request, validating every field. */
    public ProductEntity buildNew(CreateProductRequest request) {

        if (request == null) {
            throw new BadRequestException("Product request is required");
        }

        String name = requireName(request.getName());
        validatePrice(request.getPrice());
        validateCostPrice(request.getCostPrice());

        boolean trackInventory = request.getTrackInventory() == null || request.getTrackInventory();
        Integer stock = request.getStockQuantity();

        if (trackInventory) {
            if (stock == null) {
                throw new BadRequestException("Product stock quantity is required");
            }
            if (stock < 0) {
                throw new BadRequestException("Product stock quantity cannot be negative");
            }
        } else {
            stock = stock != null ? Math.max(stock, 0) : 0;
        }

        validateReorderLevel(request.getReorderLevel());

        String barcode = Barcodes.normalize(request.getBarcode());
        String sku = Texts.clean(request.getSku());
        String zraItemCode = Texts.clean(request.getZraItemCode());
        validateUniqueFields(barcode, sku, zraItemCode, null);

        ZraTaxType taxType = resolveTaxType(request.getZraTaxType(), request.getVatRate());
        BigDecimal vatRate = resolveVatRate(taxType, request.getVatRate());

        ProductEntity product = ProductEntity.builder()
                .name(name)
                .description(Texts.truncate(Texts.clean(request.getDescription()), 1000))
                .unit(resolveUnit(request.getUnitId()))
                .averageCost(Money.nz(request.getCostPrice()).setScale(4, java.math.RoundingMode.HALF_UP))
                .barcode(barcode)
                .sku(sku)
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .stockQuantity(stock)
                .active(Boolean.TRUE)
                .vatRate(vatRate)
                .zraTaxType(taxType)
                .trackInventory(trackInventory)
                .reorderLevel(request.getReorderLevel())
                .category(resolveCategory(request.getCategoryId()))
                .zraItemCode(zraItemCode)
                .zraRegistered(false)
                .build();

        applyZraCodes(product,
                request.getZraItemClassCode(),
                request.getZraItemTypeCode(),
                request.getZraOriginCountryCode(),
                request.getZraPackagingUnitCode(),
                request.getZraQuantityUnitCode(),
                true);

        return product;
    }

    /**
     * Applies a partial update to an existing product (fields left null are unchanged).
     * Stock quantity is applied too; the caller turns the difference into a stock movement.
     */
    public void applyUpdate(ProductEntity product, UpdateProductRequest request) {

        if (request == null) {
            throw new BadRequestException("Product request is required");
        }

        boolean zraRelevantChange = false;

        if (request.getName() != null) {
            String name = requireName(request.getName());
            zraRelevantChange |= !name.equals(product.getName());
            product.setName(name);
        }

        String barcode = request.getBarcode() != null ? Barcodes.normalize(request.getBarcode()) : null;
        String sku = request.getSku() != null ? Texts.clean(request.getSku()) : null;
        String zraItemCode = request.getZraItemCode() != null ? Texts.clean(request.getZraItemCode()) : null;
        validateUniqueFields(barcode, sku, zraItemCode, product.getId());

        if (request.getBarcode() != null) {
            product.setBarcode(barcode);
        }
        if (request.getSku() != null) {
            product.setSku(sku);
        }
        if (request.getZraItemCode() != null) {
            zraRelevantChange |= !java.util.Objects.equals(zraItemCode, product.getZraItemCode());
            product.setZraItemCode(zraItemCode);
        }

        if (request.getPrice() != null) {
            validatePrice(request.getPrice());
            zraRelevantChange |= product.getPrice() == null || product.getPrice().compareTo(request.getPrice()) != 0;
            product.setPrice(request.getPrice());
        }
        if (request.getDescription() != null) {
            product.setDescription(Texts.truncate(Texts.clean(request.getDescription()), 1000));
        }

        if (request.getUnitId() != null) {
            product.setUnit(resolveUnit(request.getUnitId()));
        }

        if (request.getCostPrice() != null) {
            validateCostPrice(request.getCostPrice());
            product.setCostPrice(request.getCostPrice());
            // With nothing on hand there is no average to protect: the new cost becomes the average.
            if (product.getStockQuantity() == null || product.getStockQuantity() <= 0) {
                product.setAverageCost(request.getCostPrice().setScale(4, java.math.RoundingMode.HALF_UP));
            }
        }

        if (request.getTrackInventory() != null) {
            product.setTrackInventory(request.getTrackInventory());
        }

        if (request.getStockQuantity() != null) {
            if (request.getStockQuantity() < 0) {
                throw new BadRequestException("Product stock quantity cannot be negative");
            }
            product.setStockQuantity(request.getStockQuantity());
        }

        if (request.getReorderLevel() != null) {
            validateReorderLevel(request.getReorderLevel());
            product.setReorderLevel(request.getReorderLevel());
        }

        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        if (request.getZraTaxType() != null || request.getVatRate() != null) {
            ZraTaxType taxType = request.getZraTaxType() != null
                    ? resolveTaxType(request.getZraTaxType(), request.getVatRate())
                    : resolveTaxType(null, request.getVatRate());
            BigDecimal vatRate = resolveVatRate(taxType, request.getVatRate());
            zraRelevantChange |= taxType != product.getZraTaxType();
            product.setZraTaxType(taxType);
            product.setVatRate(vatRate);
        }

        if (request.getCategoryId() != null) {
            product.setCategory(resolveCategory(request.getCategoryId()));
        }

        zraRelevantChange |= applyZraCodes(product,
                request.getZraItemClassCode(),
                request.getZraItemTypeCode(),
                request.getZraOriginCountryCode(),
                request.getZraPackagingUnitCode(),
                request.getZraQuantityUnitCode(),
                false);

        if (zraRelevantChange) {
            // The VSDC copy of the item is now stale and must be registered again.
            product.setZraRegistered(false);
        }
    }

    public UnitEntity resolveUnit(Long unitId) {

        if (unitId == null) {
            return null;
        }

        return unitRepository.findById(unitId)
                .orElseThrow(() -> new NotFoundException("Unit not found"));
    }

    public CategoryEntity resolveCategory(Long categoryId) {

        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    /** @return whether any ZRA code changed */
    private boolean applyZraCodes(
            ProductEntity product,
            String classCode,
            String typeCode,
            String originCountry,
            String packagingUnit,
            String quantityUnit,
            boolean applyDefaults
    ) {
        boolean changed = false;

        if (classCode != null) {
            String value = Texts.clean(classCode);
            if (value != null && !value.matches("[A-Za-z0-9]{1,20}")) {
                throw new BadRequestException("ZRA item class code must be 1-20 letters or digits");
            }
            changed |= !java.util.Objects.equals(value, product.getZraItemClassCode());
            product.setZraItemClassCode(value);
        }

        if (typeCode != null) {
            String value = Texts.clean(typeCode);
            if (value != null && !value.matches("[123]")) {
                throw new BadRequestException("ZRA item type code must be 1 (raw material), 2 (finished product) or 3 (service)");
            }
            changed |= !java.util.Objects.equals(value, product.getZraItemTypeCode());
            product.setZraItemTypeCode(value);
        } else if (applyDefaults && product.getZraItemTypeCode() == null) {
            product.setZraItemTypeCode(Boolean.FALSE.equals(product.getTrackInventory()) ? "3" : "2");
        }

        if (originCountry != null) {
            String value = Texts.clean(originCountry);
            if (value != null && !value.matches("[A-Za-z]{2}")) {
                throw new BadRequestException("ZRA origin country code must be a 2-letter ISO code");
            }
            value = value != null ? value.toUpperCase() : null;
            changed |= !java.util.Objects.equals(value, product.getZraOriginCountryCode());
            product.setZraOriginCountryCode(value);
        } else if (applyDefaults && product.getZraOriginCountryCode() == null) {
            product.setZraOriginCountryCode(properties.getZra().getDefaultOriginCountry());
        }

        if (packagingUnit != null) {
            String value = validateUnitCode(packagingUnit, "packaging");
            changed |= !java.util.Objects.equals(value, product.getZraPackagingUnitCode());
            product.setZraPackagingUnitCode(value);
        } else if (applyDefaults && product.getZraPackagingUnitCode() == null) {
            product.setZraPackagingUnitCode(properties.getZra().getDefaultPackagingUnitCode());
        }

        if (quantityUnit != null) {
            String value = validateUnitCode(quantityUnit, "quantity");
            changed |= !java.util.Objects.equals(value, product.getZraQuantityUnitCode());
            product.setZraQuantityUnitCode(value);
        } else if (applyDefaults && product.getZraQuantityUnitCode() == null) {
            product.setZraQuantityUnitCode(properties.getZra().getDefaultQuantityUnitCode());
        }

        return changed;
    }

    private String validateUnitCode(String raw, String label) {

        String value = Texts.clean(raw);
        if (value != null && !value.matches("[A-Za-z0-9]{1,10}")) {
            throw new BadRequestException("ZRA " + label + " unit code must be 1-10 letters or digits");
        }
        return value != null ? value.toUpperCase() : null;
    }

    private ZraTaxType resolveTaxType(String taxTypeCode, BigDecimal vatRate) {

        if (vatRate != null && vatRate.signum() < 0) {
            throw new BadRequestException("VAT rate cannot be negative");
        }

        if (taxTypeCode != null && !taxTypeCode.isBlank()) {
            ZraTaxType type = ZraTaxType.fromCode(taxTypeCode);
            if (vatRate != null && vatRate.compareTo(type.getRate()) != 0) {
                throw new BadRequestException("VAT rate " + vatRate.stripTrailingZeros().toPlainString()
                        + " does not match ZRA tax type " + type.getCode()
                        + " (" + type.getRate().stripTrailingZeros().toPlainString() + "%)");
            }
            return type;
        }

        return ZraTaxType.defaultForRate(vatRate);
    }

    private BigDecimal resolveVatRate(ZraTaxType taxType, BigDecimal requested) {

        if (taxType != null) {
            return taxType.getRate();
        }

        return requested != null ? requested : new BigDecimal("16.00");
    }

    private void validateUniqueFields(String barcode, String sku, String zraItemCode, Long productId) {

        if (barcode != null) {
            barcodeLookup.findConflicting(barcode, productId).ifPresent(existing -> {
                throw new ConflictException("Product barcode already exists: " + barcode
                        + " is assigned to '" + existing.getName() + "' (id " + existing.getId() + ")");
            });
        }

        if (sku != null) {
            productRepository.findBySku(sku)
                    .filter(existing -> !existing.getId().equals(productId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Product SKU already exists");
                    });
        }

        if (zraItemCode != null) {
            productRepository.findByZraItemCode(zraItemCode)
                    .filter(existing -> !existing.getId().equals(productId))
                    .ifPresent(existing -> {
                        throw new ConflictException("ZRA item code already exists");
                    });
        }
    }

    private String requireName(String name) {

        String value = Texts.clean(name);
        if (value == null) {
            throw new BadRequestException("Product name is required");
        }
        return value;
    }

    private void validatePrice(BigDecimal price) {

        if (price == null) {
            throw new BadRequestException("Product price is required");
        }

        if (price.signum() < 0) {
            throw new BadRequestException("Product price cannot be negative");
        }
    }

    private void validateCostPrice(BigDecimal costPrice) {

        if (costPrice != null && costPrice.signum() < 0) {
            throw new BadRequestException("Product cost price cannot be negative");
        }
    }

    private void validateReorderLevel(Integer reorderLevel) {

        if (reorderLevel != null && reorderLevel < 0) {
            throw new BadRequestException("Reorder level cannot be negative");
        }
    }
}
