package com.devkanyanta.pos.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.category.CategoryEntity;
import com.devkanyanta.pos.category.CategoryRepository;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.StockLedger;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductImportResult;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;
import com.devkanyanta.pos.shared.csv.CsvParser;
import com.devkanyanta.pos.shared.exception.ApiException;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.security.CurrentUser;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * Bulk catalogue import from CSV. Rows are matched to existing products by SKU, then barcode;
 * unmatched rows create new products. Every row is validated with the same rules as the product
 * API; invalid rows are reported and skipped, valid rows are applied in one transaction.
 *
 * <p>Recognised columns (case-insensitive, spaces/underscores ignored): name, sku, barcode, price,
 * costPrice, stockQuantity, vatRate, taxType, category, trackInventory, reorderLevel, active,
 * zraItemCode, zraItemClassCode, zraItemTypeCode, zraOriginCountryCode, zraPackagingUnitCode,
 * zraQuantityUnitCode.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    public static final int MAX_ROWS = 5000;

    private static final Map<String, String> COLUMN_ALIASES = buildAliases();

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    private final ProductRules productRules;

    private final StockLedger stockLedger;

    @Transactional
    public ProductImportResult importCsv(String content, boolean dryRun) {

        List<List<String>> rows = CsvParser.parse(content);

        if (rows.isEmpty()) {
            throw new BadRequestException("CSV file is empty");
        }

        List<String> header = rows.get(0);
        Map<Integer, String> columns = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();

        for (int i = 0; i < header.size(); i++) {
            String key = COLUMN_ALIASES.get(normalizeHeader(header.get(i)));
            if (key != null && !columns.containsValue(key)) {
                columns.put(i, key);
            } else if (!header.get(i).isBlank()) {
                ignored.add(header.get(i).trim());
            }
        }

        if (!columns.containsValue("name") && !columns.containsValue("sku") && !columns.containsValue("barcode")) {
            throw new BadRequestException("CSV header must contain at least a 'name', 'sku' or 'barcode' column");
        }

        if (rows.size() - 1 > MAX_ROWS) {
            throw new BadRequestException("CSV has more than " + MAX_ROWS + " data rows; split the file");
        }

        Map<String, CategoryEntity> categoryCache = new HashMap<>();
        List<ProductImportResult.RowResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;

        for (int r = 1; r < rows.size(); r++) {

            int line = r + 1;
            Map<String, String> values = new HashMap<>();
            List<String> row = rows.get(r);
            columns.forEach((index, key) -> {
                if (index < row.size()) {
                    values.put(key, Texts.clean(row.get(index)));
                }
            });

            ProductImportResult.RowResult.RowResultBuilder result = ProductImportResult.RowResult.builder()
                    .line(line)
                    .name(values.get("name"))
                    .sku(values.get("sku"))
                    .barcode(values.get("barcode"));

            try {
                RowOutcome outcome = applyRow(values, dryRun, categoryCache);
                result.status(outcome.status).productId(outcome.productId).errors(List.of());

                if (outcome.created) {
                    created++;
                } else {
                    updated++;
                }
            } catch (ApiException ex) {
                failed++;
                result.status("FAILED").errors(List.of(ex.getMessage()));
            }

            results.add(result.build());
        }

        return ProductImportResult.builder()
                .dryRun(dryRun)
                .totalRows(rows.size() - 1)
                .created(created)
                .updated(updated)
                .failed(failed)
                .recognisedColumns(new ArrayList<>(columns.values()))
                .ignoredColumns(ignored)
                .rows(results)
                .build();
    }

    private RowOutcome applyRow(Map<String, String> values, boolean dryRun, Map<String, CategoryEntity> categoryCache) {

        Optional<ProductEntity> existing = Optional.empty();

        if (values.get("sku") != null) {
            existing = productRepository.findBySku(values.get("sku"));
        }
        if (existing.isEmpty() && values.get("barcode") != null) {
            existing = productRepository.findByBarcode(values.get("barcode"));
        }

        String categoryName = values.get("category");

        if (existing.isPresent()) {
            ProductEntity product = existing.get();
            UpdateProductRequest request = toUpdateRequest(values);

            // Validate against a copy first so a failing row never leaves a half-updated entity.
            productRules.applyUpdate(copyOf(product), request);

            if (dryRun) {
                return new RowOutcome("WOULD_UPDATE", product.getId(), false);
            }

            int stockBefore = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            productRules.applyUpdate(product, request);

            if (categoryName != null) {
                product.setCategory(resolveCategory(categoryName, categoryCache));
            }

            int delta = (product.getStockQuantity() != null ? product.getStockQuantity() : 0) - stockBefore;
            if (delta != 0) {
                product.setStockQuantity(stockBefore);
                stockLedger.record(product, MovementType.ADJUSTMENT, delta, product.getId(),
                        "CSV_IMPORT", "Stock set by CSV import", CurrentUser.username());
            } else {
                productRepository.save(product);
            }

            return new RowOutcome("UPDATED", product.getId(), false);
        }

        if (values.get("name") == null) {
            throw new BadRequestException("Product name is required for new products");
        }

        CreateProductRequest request = toCreateRequest(values);
        ProductEntity product = productRules.buildNew(request);

        if (dryRun) {
            return new RowOutcome("WOULD_CREATE", null, true);
        }

        if (categoryName != null) {
            product.setCategory(resolveCategory(categoryName, categoryCache));
        }

        int openingStock = product.getStockQuantity();
        product.setStockQuantity(0);
        product = productRepository.save(product);

        if (Boolean.TRUE.equals(product.getTrackInventory()) && openingStock > 0) {
            stockLedger.record(product, MovementType.RESTOCK, openingStock, product.getId(),
                    "CSV_IMPORT", "Opening stock (CSV import)", CurrentUser.username());
        } else {
            product.setStockQuantity(openingStock);
        }

        return new RowOutcome("CREATED", product.getId(), true);
    }

    private CategoryEntity resolveCategory(String name, Map<String, CategoryEntity> cache) {

        return cache.computeIfAbsent(name.toLowerCase(Locale.ROOT), key ->
                categoryRepository.findByName(name)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())));
    }

    private CreateProductRequest toCreateRequest(Map<String, String> v) {

        CreateProductRequest request = new CreateProductRequest();
        request.setName(v.get("name"));
        request.setSku(v.get("sku"));
        request.setBarcode(v.get("barcode"));
        request.setPrice(decimal(v, "price"));
        request.setCostPrice(decimal(v, "costPrice"));
        request.setStockQuantity(integer(v, "stockQuantity"));
        request.setVatRate(decimal(v, "vatRate"));
        request.setTrackInventory(bool(v, "trackInventory"));
        request.setReorderLevel(integer(v, "reorderLevel"));
        request.setZraTaxType(v.get("zraTaxType"));
        request.setZraItemCode(v.get("zraItemCode"));
        request.setZraItemClassCode(v.get("zraItemClassCode"));
        request.setZraItemTypeCode(v.get("zraItemTypeCode"));
        request.setZraOriginCountryCode(v.get("zraOriginCountryCode"));
        request.setZraPackagingUnitCode(v.get("zraPackagingUnitCode"));
        request.setZraQuantityUnitCode(v.get("zraQuantityUnitCode"));

        if (request.getStockQuantity() == null) {
            request.setStockQuantity(0);
        }

        return request;
    }

    private UpdateProductRequest toUpdateRequest(Map<String, String> v) {

        UpdateProductRequest request = new UpdateProductRequest();
        request.setName(v.get("name"));
        request.setSku(v.get("sku"));
        request.setBarcode(v.get("barcode"));
        request.setPrice(decimal(v, "price"));
        request.setCostPrice(decimal(v, "costPrice"));
        request.setStockQuantity(integer(v, "stockQuantity"));
        request.setVatRate(decimal(v, "vatRate"));
        request.setTrackInventory(bool(v, "trackInventory"));
        request.setReorderLevel(integer(v, "reorderLevel"));
        request.setActive(bool(v, "active"));
        request.setZraTaxType(v.get("zraTaxType"));
        request.setZraItemCode(v.get("zraItemCode"));
        request.setZraItemClassCode(v.get("zraItemClassCode"));
        request.setZraItemTypeCode(v.get("zraItemTypeCode"));
        request.setZraOriginCountryCode(v.get("zraOriginCountryCode"));
        request.setZraPackagingUnitCode(v.get("zraPackagingUnitCode"));
        request.setZraQuantityUnitCode(v.get("zraQuantityUnitCode"));
        return request;
    }

    private static ProductEntity copyOf(ProductEntity p) {

        return ProductEntity.builder()
                .id(p.getId())
                .name(p.getName())
                .barcode(p.getBarcode())
                .sku(p.getSku())
                .price(p.getPrice())
                .costPrice(p.getCostPrice())
                .vatRate(p.getVatRate())
                .trackInventory(p.getTrackInventory())
                .stockQuantity(p.getStockQuantity())
                .reorderLevel(p.getReorderLevel())
                .active(p.getActive())
                .category(p.getCategory())
                .zraTaxType(p.getZraTaxType())
                .zraItemCode(p.getZraItemCode())
                .zraItemClassCode(p.getZraItemClassCode())
                .zraItemTypeCode(p.getZraItemTypeCode())
                .zraOriginCountryCode(p.getZraOriginCountryCode())
                .zraPackagingUnitCode(p.getZraPackagingUnitCode())
                .zraQuantityUnitCode(p.getZraQuantityUnitCode())
                .zraRegistered(p.getZraRegistered())
                .build();
    }

    private static BigDecimal decimal(Map<String, String> values, String key) {

        String raw = values.get(key);
        if (raw == null) {
            return null;
        }

        String cleaned = raw.replaceAll("(?i)^(zmw|k)\\s*", "").replace(",", "").replace(" ", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid number for '" + key + "': " + raw);
        }
    }

    private static Integer integer(Map<String, String> values, String key) {

        BigDecimal value = decimal(values, key);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("'" + key + "' must be a whole number: " + values.get(key));
        }
    }

    private static Boolean bool(Map<String, String> values, String key) {

        String raw = values.get(key);
        if (raw == null) {
            return null;
        }

        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw new BadRequestException("Invalid yes/no value for '" + key + "': " + raw);
        };
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private static Map<String, String> buildAliases() {

        Map<String, String> aliases = new HashMap<>();
        alias(aliases, "name", "name", "productname", "item", "itemname", "description");
        alias(aliases, "sku", "sku", "code", "itemcode", "productcode");
        alias(aliases, "barcode", "barcode", "ean", "upc", "bcd");
        alias(aliases, "price", "price", "sellingprice", "unitprice", "retailprice");
        alias(aliases, "costPrice", "costprice", "cost", "buyingprice", "purchaseprice");
        alias(aliases, "stockQuantity", "stockquantity", "stock", "quantity", "qty", "openingstock");
        alias(aliases, "vatRate", "vatrate", "vat", "taxrate");
        alias(aliases, "zraTaxType", "taxtype", "zrataxtype", "vatcategory", "vatcatcd");
        alias(aliases, "category", "category", "categoryname");
        alias(aliases, "trackInventory", "trackinventory", "trackstock");
        alias(aliases, "reorderLevel", "reorderlevel", "reorderpoint", "minstock", "lowstock");
        alias(aliases, "active", "active", "isactive", "enabled");
        alias(aliases, "zraItemCode", "zraitemcode", "itemcd");
        alias(aliases, "zraItemClassCode", "zraitemclasscode", "itemclasscode", "itemclscd", "classificationcode");
        alias(aliases, "zraItemTypeCode", "zraitemtypecode", "itemtypecode", "itemtycd");
        alias(aliases, "zraOriginCountryCode", "zraorigincountrycode", "origincountry", "orgnnatcd", "origin");
        alias(aliases, "zraPackagingUnitCode", "zrapackagingunitcode", "packagingunit", "pkgunitcd");
        alias(aliases, "zraQuantityUnitCode", "zraquantityunitcode", "quantityunit", "qtyunitcd", "unit", "uom");
        return aliases;
    }

    private static void alias(Map<String, String> aliases, String key, String... names) {
        for (String name : names) {
            aliases.put(name, key);
        }
    }

    private record RowOutcome(String status, Long productId, boolean created) {
    }
}
