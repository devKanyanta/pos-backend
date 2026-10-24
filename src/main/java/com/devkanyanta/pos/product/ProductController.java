package com.devkanyanta.pos.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.product.dto.ProductImportResult;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.web.PageResponse;
import com.devkanyanta.pos.product.dto.CreateProductRequest;
import com.devkanyanta.pos.product.dto.ProductResponse;
import com.devkanyanta.pos.product.dto.UpdateProductRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final ProductImportService productImportService;

    private static final String IMPORT_TEMPLATE =
            "name,sku,barcode,price,costPrice,stockQuantity,taxType,category,reorderLevel,"
            + "zraItemClassCode,zraItemTypeCode,zraPackagingUnitCode,zraQuantityUnitCode,zraOriginCountryCode\n";

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @RequestBody CreateProductRequest request
    ) {
        return productService.createProduct(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    /** Products at or below their reorder level (dashboard low-stock alert). */
    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public List<ProductResponse> getLowStockProducts() {
        return productService.getLowStockProducts();
    }

    /**
     * Bulk catalogue import from a CSV file upload (multipart field {@code file}).
     * Use {@code dryRun=true} to validate without saving.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_IMPORT + "')")
    public ProductImportResult importProducts(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun
    ) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded CSV file is empty");
        }
        return productImportService.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), dryRun);
    }

    /** Same as the multipart import, but with the CSV sent as the raw request body. */
    @PostMapping(value = "/import", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_IMPORT + "')")
    public ProductImportResult importProductsRaw(
            @RequestBody String csv,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun
    ) {
        return productImportService.importCsv(csv, dryRun);
    }

    /** Empty CSV with the recognised import columns. */
    @GetMapping(value = "/import/template", produces = "text/csv")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_IMPORT + "')")
    public ResponseEntity<String> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"product-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(IMPORT_TEMPLATE);
    }

    /**
     * Looks a product up by its scanned or typed barcode, so clients (checkout, product form) need
     * not download the catalogue. Inactive products are returned too (check {@code active}).
     */
    @GetMapping("/by-barcode")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public ProductResponse getProductByBarcode(
            @RequestParam("barcode") String barcode
    ) {
        return productService.getProductByBarcode(barcode);
    }

    /**
     * Filtered, sorted, paginated product list. {@code GET /products} still returns everything for
     * existing clients.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public PageResponse<ProductResponse> searchProducts(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "unitId", required = false) Long unitId,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "trackInventory", required = false) Boolean trackInventory,
            @RequestParam(value = "lowStock", required = false) Boolean lowStock,
            @RequestParam(value = "outOfStock", required = false) Boolean outOfStock,
            @RequestParam(value = "hasBarcode", required = false) Boolean hasBarcode,
            @RequestParam(value = "minPrice", required = false) java.math.BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) java.math.BigDecimal maxPrice,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return productService.search(new ProductSpecifications.Filter(q, categoryId, unitId, active, trackInventory,
                lowStock, outOfStock, hasBarcode, minPrice, maxPrice), page, size, sort);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_UPDATE + "')")
    public ProductResponse archiveProduct(@PathVariable Long id) {
        return productService.archiveProduct(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_UPDATE + "')")
    public ProductResponse restoreProduct(@PathVariable Long id) {
        return productService.restoreProduct(id);
    }

    @GetMapping("/{id}/price-history")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public List<ProductService.PriceChangeResponse> getPriceHistory(@PathVariable Long id) {
        return productService.getPriceHistory(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_VIEW + "')")
    public ProductResponse getProductById(
            @PathVariable Long id
    ) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_UPDATE + "')")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable Long id
    ) {
        productService.deleteProduct(id);
    }
}
