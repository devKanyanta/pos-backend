package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.InventoryTestSupport;

import tools.jackson.databind.JsonNode;

/** Inventory reports are computed from the ledger and sales, and respect filters and the plan. */
class InventoryReportsIntegrationTest extends InventoryTestSupport {

    private final String today = LocalDate.now().toString();

    private int indexOfProduct(JsonNode items, long productId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get("productId").asLong() == productId) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void costOfGoodsSoldMoversAndDepletion() {
        String admin = adminToken();
        long cat = category(admin);
        long a = product(admin, cat, "10.00", "2.00", 50).get("id").asLong();
        long b = product(admin, cat, "10.00", "2.00", 50).get("id").asLong();
        long c = product(admin, cat, "10.00", "2.00", 30).get("id").asLong(); // never sells

        assertThat(sell(admin, a, 6).status()).isEqualTo(201);
        assertThat(sell(admin, a, 4).status()).isEqualTo(201);
        assertThat(sell(admin, b, 3).status()).isEqualTo(201);
        long voidedSale = sell(admin, b, 2).json().get("id").asLong();
        api.post("/api/v1/sales/" + voidedSale + "/void", Map.of("reason", "mistake"), admin);

        // 13 units sold (the voided sale does not count): revenue 130, cost 13 x 2.
        JsonNode cogs = api.get("/api/v1/inventory/reports/cogs?from=" + today + "&to=" + today + "&categoryId=" + cat, admin).json();
        assertThat(cogs.get("unitsSold").asInt()).isEqualTo(13);
        assertThat(dec(cogs.get("revenue"))).isEqualByComparingTo("130.00");
        assertThat(dec(cogs.get("costOfGoodsSold"))).isEqualByComparingTo("26.00");
        assertThat(dec(cogs.get("grossProfit"))).isEqualByComparingTo("104.00");
        assertThat(dec(cogs.get("marginPercent"))).isEqualByComparingTo("80.00");
        assertThat(cogs.get("linesWithoutCost").asInt()).isZero();
        assertThat(cogs.get("byCategory").size()).isEqualTo(1);

        // Refunds reduce revenue and cost proportionally: one unit of A comes back.
        long cash = paymentMethodId("CASH", admin);
        JsonNode paid = api.post("/api/v1/sales", Map.of("items", List.of(Map.of("productId", a, "quantity", 2)),
                "payments", List.of(Map.of("paymentMethodId", cash))), admin).json();
        api.post("/api/v1/sales/" + paid.get("id").asLong() + "/refund", Map.of("items",
                List.of(Map.of("saleItemId", paid.get("items").get(0).get("id").asLong(), "quantity", 1))), admin);
        JsonNode afterRefund = api.get("/api/v1/inventory/reports/cogs?from=" + today + "&to=" + today + "&categoryId=" + cat, admin).json();
        assertThat(afterRefund.get("unitsSold").asInt()).isEqualTo(14);
        assertThat(afterRefund.get("unitsRefunded").asInt()).isEqualTo(1);
        assertThat(dec(afterRefund.get("revenue"))).isEqualByComparingTo("140.00");
        assertThat(dec(afterRefund.get("costOfGoodsSold"))).isEqualByComparingTo("28.00");

        // Fast movers: A outsells B; slow movers: the untouched C comes before the sellers.
        JsonNode fast = api.get("/api/v1/inventory/reports/movers?from=" + today + "&to=" + today + "&limit=500", admin).json();
        assertThat(fast.get("direction").asString()).isEqualTo("FAST");
        int fastA = indexOfProduct(fast.get("items"), a);
        int fastB = indexOfProduct(fast.get("items"), b);
        assertThat(fastA).isNotNegative();
        assertThat(fastB).isNotNegative();
        assertThat(fastA).isLessThan(fastB);
        assertThat(fast.get("items").get(fastA).get("unitsSold").asInt()).isEqualTo(11); // 10 + (2 - 1 refunded)
        assertThat(indexOfProduct(fast.get("items"), c)).isNegative();

        JsonNode slow = api.get("/api/v1/inventory/reports/movers?from=" + today + "&to=" + today + "&direction=slow&limit=500", admin).json();
        int slowC = indexOfProduct(slow.get("items"), c);
        assertThat(slowC).isNotNegative();
        assertThat(slow.get("items").get(slowC).get("unitsSold").asInt()).isZero();
        assertThat(slowC).isLessThan(indexOfProduct(slow.get("items"), a));

        // Depletion from the ledger: B sold 5 (3 + the voided 2), 2 came back through the void.
        JsonNode depletion = api.get("/api/v1/inventory/reports/depletion?from=" + today + "&to=" + today + "&categoryId=" + cat, admin).json();
        JsonNode depletionB = depletion.get(indexOfProduct(depletion, b));
        assertThat(depletionB.get("sold").asInt()).isEqualTo(5);
        assertThat(depletionB.get("voided").asInt()).isEqualTo(2);
        assertThat(depletionB.get("netDepleted").asInt()).isEqualTo(3);
        JsonNode depletionA = depletion.get(indexOfProduct(depletion, a));
        assertThat(depletionA.get("sold").asInt()).isEqualTo(12);
        assertThat(depletionA.get("returned").asInt()).isEqualTo(1);
        assertThat(depletionA.get("netDepleted").asInt()).isEqualTo(11);
        assertThat(indexOfProduct(depletion, c)).isNegative();
    }

    @Test
    void adjustmentAndStockCountReport() {
        String admin = adminToken();
        long product = product(admin, "10.00", "4.00", 30).get("id").asLong();

        api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -2, "reason", "x", "reasonCode", "DAMAGED"), admin);
        api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1, "reason", "y", "reasonCode", "LOST"), admin);
        long count = api.post("/api/v1/stock-counts", null, admin).json().get("id").asLong();
        api.put("/api/v1/stock-counts/" + count + "/lines", Map.of("lines", List.of(Map.of("productId", product, "countedQuantity", 24))), admin);
        api.post("/api/v1/stock-counts/" + count + "/commit", null, admin);

        JsonNode report = api.get("/api/v1/inventory/reports/adjustments?from=" + today + "&to=" + today, admin).json();
        JsonNode damaged = group(report, "ADJUSTMENT", "DAMAGED");
        JsonNode lost = group(report, "ADJUSTMENT", "LOST");
        JsonNode counted = group(report, "COUNT", "COUNT_VARIANCE");
        assertThat(damaged.get("unitsOut").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(lost.get("unitsOut").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(counted.get("unitsOut").asInt()).isGreaterThanOrEqualTo(3); // 27 -> 24
        assertThat(counted.get("netValue").decimalValue()).isLessThan(java.math.BigDecimal.ZERO);
        assertThat(report.get("committedCounts").asInt()).isGreaterThanOrEqualTo(1);

        // A period with nothing in it is empty, not an error.
        JsonNode empty = api.get("/api/v1/inventory/reports/adjustments?from=2000-01-01&to=2000-01-31", admin).json();
        assertThat(empty.get("groups").size()).isZero();
        assertThat(dec(empty.get("netValue"))).isEqualByComparingTo("0.00");
    }

    private JsonNode group(JsonNode report, String type, String reason) {
        for (JsonNode g : report.get("groups")) {
            if (g.get("movementType").asString().equals(type) && g.get("reason").asString().equals(reason)) {
                return g;
            }
        }
        throw new AssertionError("No " + type + "/" + reason + " group in " + report);
    }

    @Test
    void purchaseReportSummarisesReceiptsAndReturnsBySupplier() {
        String admin = adminToken();
        long product = product(admin, "10.00", "1.00", 0).get("id").asLong();
        long supplier = supplier(admin);
        long po = purchaseOrder(admin, supplier, true, product, 10, "3.00").get("id").asLong();
        receive(admin, po, product, 10);
        api.post("/api/v1/supplier-returns", Map.of("requestId", requestId(), "supplierId", supplier,
                "purchaseOrderId", po, "reason", "Damaged", "lines", List.of(Map.of("productId", product, "quantity", 2))), admin);

        JsonNode report = api.get("/api/v1/inventory/reports/purchases?from=" + today + "&to=" + today + "&supplierId=" + supplier, admin).json();
        assertThat(report.get("receipts").asInt()).isEqualTo(1);
        assertThat(report.get("units").asInt()).isEqualTo(10);
        assertThat(dec(report.get("receivedCost"))).isEqualByComparingTo("30.00");
        assertThat(dec(report.get("returnedCredit"))).isEqualByComparingTo("6.00");
        JsonNode row = report.get("bySupplier").get(0);
        assertThat(row.get("supplierId").asLong()).isEqualTo(supplier);
        assertThat(row.get("returns").asInt()).isEqualTo(1);

        // The receipt history endpoint agrees and filters by date.
        assertThat(api.get("/api/v1/goods-receipts?supplierId=" + supplier + "&from=" + today + "&to=" + today, admin)
                .json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/goods-receipts?supplierId=" + supplier + "&from=2000-01-01&to=2000-01-02", admin)
                .json().get("totalElements").asInt()).isZero();
    }

    @Test
    void reportsValidateInputAndFollowThePlan() {
        String admin = adminToken();

        assertThat(api.get("/api/v1/inventory/reports/cogs", admin).status()).isEqualTo(400); // dates required
        assertThat(api.get("/api/v1/inventory/reports/cogs?from=2026-02-01&to=2026-01-01", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/inventory/reports/movers?from=" + today + "&to=" + today + "&limit=0", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/inventory/reports/movers?from=" + today + "&to=" + today + "&direction=SIDEWAYS", admin).status()).isEqualTo(400);

        // Reports belong to the SME plan; stock levels and movements stay available on MICRO.
        api.put("/api/v1/merchant", Map.of("plan", "MICRO"), admin);
        try {
            ApiClient.Response blocked = api.get("/api/v1/inventory/reports/valuation", admin);
            assertThat(blocked.status()).isEqualTo(403);
            assertThat(blocked.json().get("message").asString()).contains("MICRO");
            assertThat(api.get("/api/v1/inventory/stock", admin).status()).isEqualTo(200);
            assertThat(api.get("/api/v1/inventory/movements", admin).status()).isEqualTo(200);
        } finally {
            api.put("/api/v1/merchant", Map.of("plan", "SME"), admin);
        }
        assertThat(api.get("/api/v1/inventory/reports/valuation", admin).status()).isEqualTo(200);
    }

    @Test
    void existingInventoryValueReportUsesTheAverageCost() {
        String admin = adminToken();
        long cat = category(admin);
        long product = product(admin, cat, "10.00", "5.00", 10).get("id").asLong();
        api.post("/api/v1/inventory-movements/restock", Map.of("productId", product, "quantity", 10, "unitCost", "9.00"), admin);

        JsonNode legacy = api.get("/api/v1/reports/inventory/value", admin).json();
        assertThat(legacy.get("totalStockValue").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("140.00"));
        JsonNode valuation = api.get("/api/v1/inventory/reports/valuation?categoryId=" + cat, admin).json();
        assertThat(dec(valuation.get("costValue"))).isEqualByComparingTo("140.00"); // 20 x 7.00
        assertThat(valuation.get("byCategory").get(0).get("units").asInt()).isEqualTo(20);
    }
}
