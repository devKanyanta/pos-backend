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
