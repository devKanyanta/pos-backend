package com.devkanyanta.pos.purchasing;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.purchasing.PurchasingDtos.CancelRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.GoodsReceiptResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.PurchaseOrderRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.PurchaseOrderResponse;
import com.devkanyanta.pos.purchasing.PurchasingDtos.ReceiptRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.SupplierReturnRequest;
import com.devkanyanta.pos.purchasing.PurchasingDtos.SupplierReturnResponse;
import com.devkanyanta.pos.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;

/** Purchase orders, goods receipts and supplier returns. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchasingController {

    private static final String VIEW = "hasAuthority('" + PermissionConstants.PURCHASE_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + PermissionConstants.PURCHASE_MANAGE + "')";
    private static final String RECEIVE = "hasAuthority('" + PermissionConstants.PURCHASE_RECEIVE + "')";

    private final PurchaseOrderService orderService;

    private final GoodsReceiptService receiptService;

    private final SupplierReturnService returnService;

    // ---- Purchase orders ----

    @PostMapping("/purchase-orders")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse createOrder(@RequestBody PurchaseOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize(VIEW)
    public PageResponse<PurchaseOrderResponse> searchOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return orderService.search(status, supplierId, from, to, q, page, size, sort);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize(VIEW)
    public PurchaseOrderResponse getOrder(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize(MANAGE)
    public PurchaseOrderResponse updateOrder(@PathVariable Long id, @RequestBody PurchaseOrderRequest request) {
        return orderService.update(id, request);
    }

    @PostMapping("/purchase-orders/{id}/place")
    @PreAuthorize(MANAGE)
    public PurchaseOrderResponse placeOrder(@PathVariable Long id) {
        return orderService.place(id);
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize(MANAGE)
    public PurchaseOrderResponse cancelOrder(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return orderService.cancel(id, request != null ? request.reason() : null);
    }

    @PostMapping("/purchase-orders/{id}/close")
    @PreAuthorize(MANAGE)
    public PurchaseOrderResponse closeOrder(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return orderService.close(id, request != null ? request.reason() : null);
    }

    // ---- Goods receipts ----

    @PostMapping("/purchase-orders/{id}/receipts")
    @PreAuthorize(RECEIVE)
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceiptResponse receive(@PathVariable Long id, @RequestBody ReceiptRequest request) {
        return receiptService.receive(id, request);
    }

    @GetMapping("/goods-receipts")
    @PreAuthorize(VIEW)
    public PageResponse<GoodsReceiptResponse> searchReceipts(
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "purchaseOrderId", required = false) Long purchaseOrderId,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return receiptService.search(supplierId, purchaseOrderId, productId, from, to, page, size, sort);
    }

    @GetMapping("/goods-receipts/{id}")
    @PreAuthorize(VIEW)
    public GoodsReceiptResponse getReceipt(@PathVariable Long id) {
        return receiptService.get(id);
    }

    // ---- Supplier returns ----

    @PostMapping("/supplier-returns")
    @PreAuthorize(RECEIVE)
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierReturnResponse createReturn(@RequestBody SupplierReturnRequest request) {
        return returnService.create(request);
    }

    @GetMapping("/supplier-returns")
    @PreAuthorize(VIEW)
    public PageResponse<SupplierReturnResponse> searchReturns(
            @RequestParam(value = "supplierId", required = false) Long supplierId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return returnService.search(supplierId, from, to, page, size, sort);
    }

    @GetMapping("/supplier-returns/{id}")
    @PreAuthorize(VIEW)
    public SupplierReturnResponse getReturn(@PathVariable Long id) {
        return returnService.get(id);
    }
}
