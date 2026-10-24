package com.devkanyanta.pos.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes for suppliers' purchase orders, goods receipts and returns. */
public final class PurchasingDtos {

    private PurchasingDtos() {
    }

    // ---- Purchase orders ----------------------------------------------------------------------

    public record OrderLineRequest(Long productId, Integer quantity, BigDecimal unitCost) {
    }

    /** {@code order=true} creates the order already placed (ORDERED) instead of as a DRAFT. */
    public record PurchaseOrderRequest(Long supplierId, LocalDate orderDate, LocalDate expectedDate,
                                       String supplierReference, String notes, List<OrderLineRequest> lines,
                                       Boolean order) {
    }

    public record CancelRequest(String reason) {
    }

    public record OrderLineResponse(Long id, Long productId, String productName, String sku, String barcode,
                                    int orderedQuantity, int receivedQuantity, int outstandingQuantity,
                                    int returnedQuantity, BigDecimal unitCost, BigDecimal lineCost) {
    }

    public record PurchaseOrderResponse(Long id, String poNumber, Long supplierId, String supplierName, String status,
                                        LocalDate orderDate, LocalDate expectedDate, String supplierReference,
                                        String notes, BigDecimal totalCost, String createdBy, String orderedBy,
                                        LocalDateTime orderedAt, String cancelledBy, LocalDateTime cancelledAt,
                                        String cancelReason, LocalDateTime createdAt, List<OrderLineResponse> lines) {
    }

    // ---- Goods receipts -----------------------------------------------------------------------

    /** Identify the order line by {@code poLineId} or by {@code productId}. */
    public record ReceiptLineRequest(Long poLineId, Long productId, Integer quantity, BigDecimal unitCost) {
    }

    /** {@code requestId} is required: reusing it returns the original receipt and receives nothing twice. */
    public record ReceiptRequest(String requestId, String supplierInvoiceNumber, String notes,
                                 List<ReceiptLineRequest> lines) {
    }

    public record ReceiptLineResponse(Long id, Long poLineId, Long productId, String productName,
                                      int quantity, BigDecimal unitCost, BigDecimal totalCost) {
    }

    public record GoodsReceiptResponse(Long id, String receiptNumber, Long purchaseOrderId, String poNumber,
                                       Long supplierId, String supplierName, String supplierInvoiceNumber,
                                       String notes, String receivedBy, LocalDateTime receivedAt,
                                       BigDecimal totalCost, String purchaseOrderStatus,
                                       List<ReceiptLineResponse> lines) {
    }

    // ---- Supplier returns ---------------------------------------------------------------------

    public record ReturnLineRequest(Long productId, Integer quantity, BigDecimal creditUnitCost) {
    }

    public record SupplierReturnRequest(String requestId, Long supplierId, Long purchaseOrderId, String reason,
                                        List<ReturnLineRequest> lines) {
    }

    public record ReturnLineResponse(Long id, Long productId, String productName, int quantity,
                                     BigDecimal creditUnitCost, BigDecimal creditTotal, BigDecimal stockUnitCost) {
    }

    public record SupplierReturnResponse(Long id, String returnNumber, Long supplierId, String supplierName,
                                         Long purchaseOrderId, String poNumber, String reason, String returnedBy,
                                         LocalDateTime returnedAt, BigDecimal creditTotal,
                                         List<ReturnLineResponse> lines) {
    }
}
