package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.InventoryTestSupport;

import tools.jackson.databind.JsonNode;

/** Suppliers, purchase orders, goods receipts, supplier returns and stock counts. */
class PurchasingIntegrationTest extends InventoryTestSupport {

    @Test
    void orderingDoesNotChangeStockAndReceivingDoes() {
        String admin = adminToken();
        long product = this.product(admin, "20.00", "5.00", 10).get("id").asLong();
        long supplier = supplier(admin);

        JsonNode po = purchaseOrder(admin, supplier, true, product, 10, "8.00");
        assertThat(po.get("status").asString()).isEqualTo("ORDERED");
        assertThat(po.get("poNumber").asString()).startsWith("PO-");
        assertThat(dec(po.get("totalCost"))).isEqualByComparingTo("80.00");
        assertThat(stock(admin, product)).isEqualTo(10); // ordering moves nothing

        ApiClient.Response received = receive(admin, po.get("id").asLong(), product, 10);
        assertThat(received.status()).as(received.body()).isEqualTo(201);
        assertThat(received.json().get("receiptNumber").asString()).startsWith("GRN-");
        assertThat(received.json().get("purchaseOrderStatus").asString()).isEqualTo("RECEIVED");

        assertThat(stock(admin, product)).isEqualTo(20);
        // (10 x 5.00 + 10 x 8.00) / 20 = 6.50 average; 8.00 is now the latest purchase cost.
        assertThat(averageCost(admin, product)).isEqualByComparingTo("6.5");
        assertThat(getProduct(admin, product).get("costPrice").decimalValue()).isEqualByComparingTo("8.00");

        JsonNode ledger = movements(admin, product);
        assertThat(ledger.size()).isEqualTo(2);
        assertThat(ledger.get(0).get("movementType").asString()).isEqualTo("OPENING");
        assertThat(ledger.get(0).get("balanceAfter").asInt()).isEqualTo(10);
        JsonNode receipt = last(ledger);
        assertThat(receipt.get("movementType").asString()).isEqualTo("PURCHASE_RECEIPT");
        assertThat(receipt.get("quantity").asInt()).isEqualTo(10);
        assertThat(receipt.get("balanceAfter").asInt()).isEqualTo(20);
        assertThat(receipt.get("unitCost").decimalValue()).isEqualByComparingTo("8");
        assertThat(receipt.get("referenceType").asString()).isEqualTo("GOODS_RECEIPT");
        assertThat(receipt.get("performedBy").asString()).isEqualTo(ADMIN);
    }

    @Test
    void partialDeliveriesAreTrackedAndOverReceiptIsRefused() {
        String admin = adminToken();
        long a = product(admin, "10.00", "1.00", 0).get("id").asLong();
        long b = product(admin, "10.00", "1.00", 0).get("id").asLong();
        long supplier = supplier(admin);
        long poId = purchaseOrder(admin, supplier, true, a, 10, "2.00", b, 5, "3.00").get("id").asLong();

        ApiClient.Response first = receive(admin, poId, a, 6);
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.json().get("purchaseOrderStatus").asString()).isEqualTo("PARTIALLY_RECEIVED");

        JsonNode order = api.get("/api/v1/purchase-orders/" + poId, admin).json();
        assertThat(order.get("status").asString()).isEqualTo("PARTIALLY_RECEIVED");
        JsonNode lineA = order.get("lines").get(0);
        assertThat(lineA.get("receivedQuantity").asInt()).isEqualTo(6);
        assertThat(lineA.get("outstandingQuantity").asInt()).isEqualTo(4);

        // Over-receiving is refused and changes nothing.
        ApiClient.Response over = receive(admin, poId, a, 5);
        assertThat(over.status()).isEqualTo(400);
        assertThat(over.json().get("message").asString()).contains("only 4 outstanding");
        assertThat(stock(admin, a)).isEqualTo(6);

        // A second delivery completes the order, with the invoiced cost differing from the order's.
        ApiClient.Response second = api.post("/api/v1/purchase-orders/" + poId + "/receipts", Map.of(
                "requestId", requestId(), "supplierInvoiceNumber", "INV-77",
                "lines", List.of(Map.of("productId", a, "quantity", 4, "unitCost", "4.00"),
                        Map.of("productId", b, "quantity", 5))), admin);
        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(second.json().get("purchaseOrderStatus").asString()).isEqualTo("RECEIVED");
        assertThat(second.json().get("supplierInvoiceNumber").asString()).isEqualTo("INV-77");
        assertThat(stock(admin, a)).isEqualTo(10);
        assertThat(stock(admin, b)).isEqualTo(5);
        // 6 @ 2.00 then 4 @ 4.00 -> (12 + 16) / 10 = 2.80.
        assertThat(averageCost(admin, a)).isEqualByComparingTo("2.8");

        // Nothing more can be received on a completed order, and both receipts are listed.
        assertThat(receive(admin, poId, a, 1).status()).isEqualTo(409);
        JsonNode receipts = api.get("/api/v1/goods-receipts?purchaseOrderId=" + poId, admin).json();
        assertThat(receipts.get("totalElements").asInt()).isEqualTo(2);
        assertThat(api.get("/api/v1/goods-receipts?productId=" + b, admin).json().get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void receiptsAreIdempotent() throws Exception {
        String admin = adminToken();
        long product = this.product(admin, "10.00", "1.00", 0).get("id").asLong();
        long supplier = supplier(admin);
        long poId = purchaseOrder(admin, supplier, true, product, 20, "2.00").get("id").asLong();

        Map<String, Object> body = Map.of("requestId", requestId(),
                "lines", List.of(Map.of("productId", product, "quantity", 5)));
        ApiClient.Response first = api.post("/api/v1/purchase-orders/" + poId + "/receipts", body, admin);
        ApiClient.Response again = api.post("/api/v1/purchase-orders/" + poId + "/receipts", body, admin);
        assertThat(first.status()).isEqualTo(201);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.json().get("id").asLong()).isEqualTo(first.json().get("id").asLong());
        assertThat(stock(admin, product)).isEqualTo(5); // received once

        // The key cannot be reused for a different order.
        long otherPo = purchaseOrder(admin, supplier, true, product, 5, "2.00").get("id").asLong();
        assertThat(api.post("/api/v1/purchase-orders/" + otherPo + "/receipts", body, admin).status()).isEqualTo(409);

        // Concurrent duplicates: one receipt, stock added once.
        Map<String, Object> raceBody = Map.of("requestId", requestId(),
                "lines", List.of(Map.of("productId", product, "quantity", 7)));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ApiClient.Response>> calls = IntStream.range(0, 4)
                    .<Callable<ApiClient.Response>>mapToObj(i -> () -> api.post("/api/v1/purchase-orders/" + poId + "/receipts", raceBody, admin))
                    .toList();
            for (Future<ApiClient.Response> f : pool.invokeAll(calls)) {
                assertThat(f.get().status()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(stock(admin, product)).isEqualTo(12);
        assertThat(api.get("/api/v1/goods-receipts?purchaseOrderId=" + poId, admin).json().get("totalElements").asInt()).isEqualTo(2);

        // requestId is mandatory and validated.
        assertThat(api.post("/api/v1/purchase-orders/" + poId + "/receipts",
                Map.of("lines", List.of(Map.of("productId", product, "quantity", 1))), admin).status()).isEqualTo(400);
    }

    @Test
    void orderLifecycle_draftEditCancelAndClose() {
        String admin = adminToken();
        long product = this.product(admin, "10.00", "1.00", 0).get("id").asLong();
        long supplier = supplier(admin);

        JsonNode draft = purchaseOrder(admin, supplier, false, product, 10, "2.00");
        long id = draft.get("id").asLong();
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(receive(admin, id, product, 1).status()).isEqualTo(409); // not placed yet

        ApiClient.Response edited = api.put("/api/v1/purchase-orders/" + id, Map.of(
                "notes", "urgent", "lines", List.of(Map.of("productId", product, "quantity", 12, "unitCost", "2.50"))), admin);
        assertThat(edited.status()).as(edited.body()).isEqualTo(200);
        assertThat(dec(edited.json().get("totalCost"))).isEqualByComparingTo("30.00");

        assertThat(api.post("/api/v1/purchase-orders/" + id + "/place", null, admin).json().get("status").asString())
                .isEqualTo("ORDERED");
        assertThat(api.put("/api/v1/purchase-orders/" + id, Map.of("notes", "late"), admin).status()).isEqualTo(409);

        // Cancelling an order with nothing received leaves stock alone.
        ApiClient.Response cancelled = api.post("/api/v1/purchase-orders/" + id + "/cancel", Map.of("reason", "supplier out of stock"), admin);
        assertThat(cancelled.json().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.json().get("cancelReason").asString()).isEqualTo("supplier out of stock");
        assertThat(receive(admin, id, product, 1).status()).isEqualTo(409);
        assertThat(stock(admin, product)).isZero();

        // Once goods arrived, the order can only be closed (short delivery accepted), not cancelled.
        long partial = purchaseOrder(admin, supplier, true, product, 10, "2.00").get("id").asLong();
        receive(admin, partial, product, 4);
        assertThat(api.post("/api/v1/purchase-orders/" + partial + "/cancel", null, admin).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/purchase-orders/" + partial + "/close", Map.of("reason", "short delivery"), admin)
                .json().get("status").asString()).isEqualTo("CLOSED");
        assertThat(receive(admin, partial, product, 1).status()).isEqualTo(409);
        assertThat(stock(admin, product)).isEqualTo(4);

        // Validation.
        assertThat(api.post("/api/v1/purchase-orders", Map.of("supplierId", supplier, "lines", List.of()), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/purchase-orders", Map.of("supplierId", supplier, "lines",
                List.of(Map.of("productId", product, "quantity", 0))), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/purchase-orders", Map.of("supplierId", 99999999, "lines",
                List.of(Map.of("productId", product, "quantity", 1))), admin).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/purchase-orders?status=BOGUS", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/purchase-orders?status=CLOSED&supplierId=" + supplier, admin)
                .json().get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void supplierReturnsRemoveStockWithoutTouchingHistory() {
        String admin = adminToken();
        long product = this.product(admin, "10.00", "0.00", 0).get("id").asLong();
        long supplier = supplier(admin);
        long poId = purchaseOrder(admin, supplier, true, product, 10, "4.00").get("id").asLong();
        receive(admin, poId, product, 10);

        Map<String, Object> request = Map.of("requestId", requestId(), "supplierId", supplier,
                "purchaseOrderId", poId, "reason", "Faulty batch",
                "lines", List.of(Map.of("productId", product, "quantity", 4)));
        ApiClient.Response returned = api.post("/api/v1/supplier-returns", request, admin);
        assertThat(returned.status()).as(returned.body()).isEqualTo(201);
        assertThat(returned.json().get("returnNumber").asString()).startsWith("SRN-");
        assertThat(dec(returned.json().get("creditTotal"))).isEqualByComparingTo("16.00");
        assertThat(stock(admin, product)).isEqualTo(6);

        JsonNode movement = last(movements(admin, product));
        assertThat(movement.get("movementType").asString()).isEqualTo("SUPPLIER_RETURN");
        assertThat(movement.get("quantity").asInt()).isEqualTo(-4);
        assertThat(movement.get("balanceAfter").asInt()).isEqualTo(6);
        assertThat(movement.get("unitCost").decimalValue()).isEqualByComparingTo("4");

        // Replaying the request does not return the goods twice.
        assertThat(api.post("/api/v1/supplier-returns", request, admin).json().get("id").asLong())
                .isEqualTo(returned.json().get("id").asLong());
        assertThat(stock(admin, product)).isEqualTo(6);

        // The purchase order and its receipts keep their original figures.
        JsonNode order = api.get("/api/v1/purchase-orders/" + poId, admin).json();
        assertThat(order.get("status").asString()).isEqualTo("RECEIVED");
        assertThat(order.get("lines").get(0).get("receivedQuantity").asInt()).isEqualTo(10);
        assertThat(order.get("lines").get(0).get("returnedQuantity").asInt()).isEqualTo(4);

        // Only delivered, not-yet-returned goods can go back.
        ApiClient.Response tooMany = api.post("/api/v1/supplier-returns", Map.of("requestId", requestId(),
                "supplierId", supplier, "purchaseOrderId", poId, "reason", "x",
                "lines", List.of(Map.of("productId", product, "quantity", 7))), admin);
        assertThat(tooMany.status()).isEqualTo(400);
        assertThat(tooMany.json().get("message").asString()).contains("only 6");

        // Stock that has already been sold cannot be returned.
        assertThat(sell(admin, product, 5).status()).isEqualTo(201);
        ApiClient.Response noStock = api.post("/api/v1/supplier-returns", Map.of("requestId", requestId(),
                "supplierId", supplier, "reason", "x", "lines", List.of(Map.of("productId", product, "quantity", 2))), admin);
        assertThat(noStock.status()).isEqualTo(400);
        assertThat(noStock.json().get("message").asString()).contains("Insufficient stock");
        assertThat(stock(admin, product)).isEqualTo(1);

        assertThat(api.post("/api/v1/supplier-returns", Map.of("requestId", requestId(), "supplierId", supplier,
                "lines", List.of(Map.of("productId", product, "quantity", 1))), admin).status()).isEqualTo(400); // no reason
        assertThat(api.get("/api/v1/supplier-returns?supplierId=" + supplier, admin).json().get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void stockCountsReconcileDiscrepanciesThroughTheLedger() {
        String admin = adminToken();
        long product = this.product(admin, "10.00", "3.00", 20).get("id").asLong();

        long countId = api.post("/api/v1/stock-counts", Map.of("notes", "Shelf A"), admin).json().get("id").asLong();
        ApiClient.Response counted = api.put("/api/v1/stock-counts/" + countId + "/lines", Map.of("lines",
                List.of(Map.of("productId", product, "countedQuantity", 17))), admin);
        assertThat(counted.status()).as(counted.body()).isEqualTo(200);
        assertThat(counted.json().get("lines").get(0).get("expectedQuantity").asInt()).isEqualTo(20);
        assertThat(stock(admin, product)).isEqualTo(20); // nothing happens before commit

        JsonNode committed = api.post("/api/v1/stock-counts/" + countId + "/commit", null, admin).json();
        assertThat(committed.get("status").asString()).isEqualTo("COMMITTED");
        assertThat(committed.get("totalShortage").asInt()).isEqualTo(3);
        assertThat(dec(committed.get("netVarianceValue"))).isEqualByComparingTo("-9.00");
        assertThat(stock(admin, product)).isEqualTo(17);
        JsonNode movement = last(movements(admin, product));
        assertThat(movement.get("movementType").asString()).isEqualTo("COUNT");
        assertThat(movement.get("quantity").asInt()).isEqualTo(-3);
        assertThat(movement.get("balanceAfter").asInt()).isEqualTo(17);

        // A committed count is final.
        assertThat(api.post("/api/v1/stock-counts/" + countId + "/commit", null, admin).status()).isEqualTo(409);
        assertThat(api.put("/api/v1/stock-counts/" + countId + "/lines", Map.of("lines",
                List.of(Map.of("productId", product, "countedQuantity", 1))), admin).status()).isEqualTo(409);

        // Sales made while a count is open are preserved: the variance is applied, not an absolute overwrite.
        long second = api.post("/api/v1/stock-counts", null, admin).json().get("id").asLong();
        api.put("/api/v1/stock-counts/" + second + "/lines", Map.of("lines",
                List.of(Map.of("productId", product, "countedQuantity", 14))), admin);
        assertThat(sell(admin, product, 2).status()).isEqualTo(201); // stock 15
        api.post("/api/v1/stock-counts/" + second + "/commit", null, admin);
        assertThat(stock(admin, product)).isEqualTo(12); // 15 + (14 - 17)

        // A count that would make stock negative is rejected as a whole and stays open.
        long third = api.post("/api/v1/stock-counts", null, admin).json().get("id").asLong();
        api.put("/api/v1/stock-counts/" + third + "/lines", Map.of("lines",
                List.of(Map.of("productId", product, "countedQuantity", 0))), admin);
        assertThat(sell(admin, product, 11).status()).isEqualTo(201); // stock 1, expected was 12
        ApiClient.Response rejected = api.post("/api/v1/stock-counts/" + third + "/commit", null, admin);
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(stock(admin, product)).isEqualTo(1);
        assertThat(api.get("/api/v1/stock-counts/" + third, admin).json().get("status").asString()).isEqualTo("OPEN");
        assertThat(api.post("/api/v1/stock-counts/" + third + "/cancel", null, admin).json().get("status").asString())
                .isEqualTo("CANCELLED");

        assertThat(api.post("/api/v1/stock-counts/" + api.post("/api/v1/stock-counts", null, admin).json().get("id").asLong()
                + "/commit", null, admin).status()).isEqualTo(400); // nothing counted
        assertThat(api.get("/api/v1/stock-counts?status=COMMITTED", admin).status()).isEqualTo(200);
    }
}
