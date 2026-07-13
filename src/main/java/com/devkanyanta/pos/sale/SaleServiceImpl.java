package com.devkanyanta.pos.sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devkanyanta.pos.customer.CustomerEntity;
import com.devkanyanta.pos.customer.CustomerRepository;
import com.devkanyanta.pos.inventory.InventoryMovementEntity;
import com.devkanyanta.pos.inventory.InventoryMovementEntity.MovementType;
import com.devkanyanta.pos.inventory.InventoryMovementRepository;
import com.devkanyanta.pos.product.ProductEntity;
import com.devkanyanta.pos.product.ProductRepository;
import com.devkanyanta.pos.sale.SaleEntity.PaymentStatus;
import com.devkanyanta.pos.sale.SaleEntity.SaleStatus;
import com.devkanyanta.pos.sale.SaleItemEntity.ItemStatus;
import com.devkanyanta.pos.sale.dto.CreateSaleRequest;
import com.devkanyanta.pos.sale.dto.RefundRequest;
import com.devkanyanta.pos.sale.dto.SaleItemRequest;
import com.devkanyanta.pos.sale.dto.SaleItemResponse;
import com.devkanyanta.pos.sale.dto.SaleResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    private final ProductRepository productRepository;

    private final CustomerRepository customerRepository;

    private final InventoryMovementRepository inventoryMovementRepository;

    private final SaleNumberGenerator saleNumberGenerator;

    @Override
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request, String username) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Sale must have at least one item");
        }

        BigDecimal saleDiscount = request.getDiscountAmount() != null
                ? request.getDiscountAmount()
                : BigDecimal.ZERO;

        if (saleDiscount.signum() < 0) {
            throw new RuntimeException("Sale discount cannot be negative");
        }

        SaleEntity sale = new SaleEntity();
        sale.setSaleNumber(saleNumberGenerator.generateSaleNumber());
        sale.setNotes(request.getNotes());
        sale.setCreatedBy(username != null ? username : "system");
        sale.setDiscountAmount(saleDiscount);
        sale.setItems(new ArrayList<>());

        // Resolve customer if provided
        if (request.getCustomerId() != null) {
            CustomerEntity customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            sale.setCustomer(customer);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        // Track products and their sold quantities for inventory movements
        Map<Long, Integer> productQuantities = new HashMap<>();
        // Also track which products need stock updates
        List<ProductEntity> productsToUpdate = new ArrayList<>();

        for (SaleItemRequest itemReq : request.getItems()) {

            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new RuntimeException("Item quantity must be positive");
            }

            ProductEntity product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemReq.getProductId()));

            // Check stock
            if (product.getTrackInventory() && product.getStockQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName()
                        + ". Available: " + product.getStockQuantity()
                        + ", requested: " + itemReq.getQuantity());
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal vatRate = product.getVatRate() != null ? product.getVatRate() : BigDecimal.ZERO;
            BigDecimal itemDiscount = itemReq.getDiscountAmount() != null
                    ? itemReq.getDiscountAmount()
                    : BigDecimal.ZERO;

            if (itemDiscount.signum() < 0) {
                throw new RuntimeException("Item discount cannot be negative for product: " + product.getName());
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            BigDecimal itemVat = lineTotal.multiply(vatRate).divide(BigDecimal.valueOf(100));

            subtotal = subtotal.add(lineTotal);
            totalVat = totalVat.add(itemVat);

            // Build sale item
            SaleItemEntity saleItem = SaleItemEntity.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemReq.getQuantity())
                    .lineTotal(lineTotal)
                    .vatRate(vatRate)
                    .vatAmount(itemVat)
                    .discountAmount(itemDiscount)
                    .build();

            sale.getItems().add(saleItem);

            // Reduce stock for tracked inventory items
            if (product.getTrackInventory()) {
                product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
                productQuantities.merge(product.getId(), itemReq.getQuantity(), Integer::sum);
                if (!productsToUpdate.stream().anyMatch(p -> p.getId().equals(product.getId()))) {
                    productsToUpdate.add(product);
                }
            }
        }

        sale.setSubtotal(subtotal);

        BigDecimal total = subtotal.subtract(saleDiscount).add(totalVat);
        if (total.signum() < 0) {
            throw new RuntimeException("Total amount cannot be negative");
        }

        sale.setVatAmount(totalVat);
        sale.setTotalAmount(total);

        // Save sale first to generate ID, then create movements with reference
        saleRepository.save(sale);

        List<InventoryMovementEntity> movements = new ArrayList<>();
        for (ProductEntity product : productsToUpdate) {
            productRepository.save(product);

            int soldQty = productQuantities.getOrDefault(product.getId(), 0);
            InventoryMovementEntity movement = InventoryMovementEntity.builder()
                    .product(product)
                    .movementType(MovementType.SALE)
                    .quantity(-soldQty)
                    .referenceId(sale.getId())
                    .referenceType("SALE")
                    .notes("Sale: " + sale.getSaleNumber())
                    .performedBy(username != null ? username : "system")
                    .build();

            movements.add(movement);
        }

        inventoryMovementRepository.saveAll(movements);

        return toResponse(sale);
    }

    @Override
    public List<SaleResponse> getAllSales() {

        return saleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SaleResponse getSaleById(Long id) {

        return toResponse(findSaleById(id));
    }

    @Override
    public SaleResponse getSaleByNumber(String saleNumber) {

        SaleEntity sale = saleRepository.findBySaleNumber(saleNumber)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + saleNumber));

        return toResponse(sale);
    }

    @Override
    @Transactional
    public SaleResponse voidSale(Long id) {

        SaleEntity sale = findSaleById(id);

        if (sale.getSaleStatus() != SaleStatus.ACTIVE) {
            throw new RuntimeException("Cannot void a sale that is " + sale.getSaleStatus());
        }

        if (sale.getPaymentStatus() == PaymentStatus.PAID || sale.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
            throw new RuntimeException("Cannot void a sale that has payments. Use refund instead.");
        }

        sale.setSaleStatus(SaleStatus.VOIDED);

        // Reverse stock changes
        for (SaleItemEntity item : sale.getItems()) {
            if (item.getProduct() != null && item.getProduct().getTrackInventory()) {
                ProductEntity product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);

                InventoryMovementEntity movement = InventoryMovementEntity.builder()
                        .product(product)
                        .movementType(MovementType.ADJUSTMENT)
                        .quantity(item.getQuantity())
                        .referenceId(sale.getId())
                        .referenceType("SALE_VOID")
                        .notes("Voided sale: " + sale.getSaleNumber())
                        .performedBy("system")
                        .build();

                inventoryMovementRepository.save(movement);
            }
        }

        return toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse refundSale(Long id, RefundRequest request) {

        SaleEntity sale = findSaleById(id);

        if (sale.getSaleStatus() == SaleStatus.VOIDED) {
            throw new RuntimeException("Cannot refund a voided sale");
        }

        if (sale.getSaleStatus() == SaleStatus.REFUNDED) {
            throw new RuntimeException("Sale is already refunded");
        }

        List<SaleItemEntity> itemsToRefund;

        if (request.getSaleItemIds() != null && !request.getSaleItemIds().isEmpty()) {
            // Partial refund: specific items
            itemsToRefund = sale.getItems().stream()
                    .filter(item -> request.getSaleItemIds().contains(item.getId()))
                    .collect(Collectors.toList());

            if (itemsToRefund.isEmpty()) {
                throw new RuntimeException("No valid sale items found to refund");
            }
        } else {
            // Full refund: all items
            itemsToRefund = new ArrayList<>(sale.getItems());
        }

        for (SaleItemEntity item : itemsToRefund) {

            if (item.getItemStatus() == ItemStatus.REFUNDED) {
                throw new RuntimeException("Item already refunded: " + item.getProductName());
            }

            int refundQty = item.getQuantity() - item.getRefundedQuantity();
            item.setRefundedQuantity(item.getQuantity());
            item.setItemStatus(ItemStatus.REFUNDED);

            // Restore stock
            if (item.getProduct() != null && item.getProduct().getTrackInventory()) {
                ProductEntity product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + refundQty);
                productRepository.save(product);

                InventoryMovementEntity movement = InventoryMovementEntity.builder()
                        .product(product)
                        .movementType(MovementType.RETURN)
                        .quantity(refundQty)
                        .referenceId(sale.getId())
                        .referenceType("SALE_REFUND")
                        .notes("Refund: " + sale.getSaleNumber() + (request.getReason() != null ? " - " + request.getReason() : ""))
                        .performedBy("system")
                        .build();

                inventoryMovementRepository.save(movement);
            }
        }

        // If all items are refunded, mark sale as refunded
        boolean allRefunded = sale.getItems().stream()
                .allMatch(item -> item.getItemStatus() == ItemStatus.REFUNDED);

        if (allRefunded) {
            sale.setSaleStatus(SaleStatus.REFUNDED);
            sale.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        return toResponse(saleRepository.save(sale));
    }

    @Override
    public List<SaleResponse> getSalesByDateRange(LocalDate from, LocalDate to) {

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private SaleEntity findSaleById(Long id) {

        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found"));
    }

    private SaleResponse toResponse(SaleEntity sale) {

        SaleResponse.SaleResponseBuilder builder = SaleResponse.builder()
                .id(sale.getId())
                .saleNumber(sale.getSaleNumber())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .vatAmount(sale.getVatAmount())
                .totalAmount(sale.getTotalAmount())
                .paymentStatus(sale.getPaymentStatus().name())
                .saleStatus(sale.getSaleStatus().name())
                .notes(sale.getNotes())
                .createdBy(sale.getCreatedBy())
                .createdAt(sale.getCreatedAt());

        if (sale.getCustomer() != null) {
            builder.customerId(sale.getCustomer().getId())
                    .customerName(
                            (sale.getCustomer().getFirstName() != null ? sale.getCustomer().getFirstName() + " " : "")
                            + (sale.getCustomer().getLastName() != null ? sale.getCustomer().getLastName() : "")
                    )
                    .customerPhone(sale.getCustomer().getPhone());
        }

        builder.items(sale.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList()));

        return builder.build();
    }

    private SaleItemResponse toItemResponse(SaleItemEntity item) {

        return SaleItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .vatRate(item.getVatRate())
                .vatAmount(item.getVatAmount())
                .discountAmount(item.getDiscountAmount())
                .itemStatus(item.getItemStatus().name())
                .refundedQuantity(item.getRefundedQuantity())
                .build();
    }
}
