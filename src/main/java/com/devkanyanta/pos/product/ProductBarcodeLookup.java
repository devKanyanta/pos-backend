package com.devkanyanta.pos.product;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.devkanyanta.pos.shared.util.Barcodes;

import lombok.RequiredArgsConstructor;

/**
 * Finds products by barcode. Shared by the product API, the sale workflow, CSV import and the
 * duplicate checks so they all agree on what "the same barcode" means (see {@link Barcodes}).
 */
@Component
@RequiredArgsConstructor
public class ProductBarcodeLookup {

    private final ProductRepository productRepository;

    /**
     * The product carrying this barcode (or its UPC-A/EAN-13 equivalent), including inactive ones.
     * An exact match wins; otherwise the oldest product, which keeps the answer stable should legacy
     * data ever contain two equivalent codes.
     */
    public Optional<ProductEntity> find(String rawBarcode) {

        String canonical = Barcodes.canonical(rawBarcode);
        if (canonical == null) {
            return Optional.empty();
        }

        return productRepository.findByBarcodeIn(Barcodes.equivalents(canonical)).stream()
                .min(Comparator
                        .comparing((ProductEntity p) -> !canonical.equals(p.getBarcode()))
                        .thenComparing(ProductEntity::getId));
    }

    /** A product other than {@code exceptProductId} that already uses this barcode. */
    public Optional<ProductEntity> findConflicting(String rawBarcode, Long exceptProductId) {

        String canonical = Barcodes.canonical(rawBarcode);
        if (canonical == null) {
            return Optional.empty();
        }

        return productRepository.findByBarcodeIn(Barcodes.equivalents(canonical)).stream()
                .filter(p -> !p.getId().equals(exceptProductId))
                .min(Comparator.comparing(ProductEntity::getId));
    }
}
