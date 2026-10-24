package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.InventoryTestSupport;
import com.devkanyanta.pos.user.role_permissions.PermissionRepository;
import com.devkanyanta.pos.user.role.RoleEntity;

import tools.jackson.databind.JsonNode;

/** The stock ledger, costing, adjustments, sales integration, catalogue management and permissions. */
class InventoryIntegrationTest extends InventoryTestSupport {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private com.devkanyanta.pos.shared.migration.SchemaMigrationRunner migrationRunner;

    @Test
    void ledgerKeepsBalancesAndWeightedAverageCost() {
        String admin = adminToken();
        long cat = category(admin);
        long product = product(admin, cat, "20.00", "5.00", 10).get("id").asLong();
        assertThat(averageCost(admin, product)).isEqualByComparingTo("5");

        // Receive 10 @ 8.00: (10 x 5 + 10 x 8) / 20 = 6.50.
        api.post("/api/v1/inventory-movements/restock", Map.of("productId", product, "quantity", 10, "unitCost", "8.00"), admin);
        assertThat(averageCost(admin, product)).isEqualByComparingTo("6.5");

        // A sale takes stock out at the average and never changes it.
        assertThat(sell(admin, product, 4).status()).isEqualTo(201);
        assertThat(stock(admin, product)).isEqualTo(16);
        assertThat(averageCost(admin, product)).isEqualByComparingTo("6.5");
        JsonNode saleMovement = last(movements(admin, product));
        assertThat(saleMovement.get("movementType").asString()).isEqualTo("SALE");
        assertThat(saleMovement.get("quantity").asInt()).isEqualTo(-4);
        assertThat(saleMovement.get("balanceAfter").asInt()).isEqualTo(16);
        assertThat(saleMovement.get("unitCost").decimalValue()).isEqualByComparingTo("6.5");
        assertThat(saleMovement.get("value").decimalValue()).isEqualByComparingTo("-26.00");

        // Receive 4 @ 10.00: (16 x 6.5 + 4 x 10) / 20 = 7.20.
        api.post("/api/v1/inventory-movements/restock", Map.of("productId", product, "quantity", 4, "unitCost", "10.00"), admin);
        assertThat(averageCost(admin, product)).isEqualByComparingTo("7.2");

        // Every balance is the running sum of the ledger.
        JsonNode ledger = movements(admin, product);
        int running = 0;
        for (JsonNode movement : ledger) {
            running += movement.get("quantity").asInt();
            assertThat(movement.get("balanceAfter").asInt()).isEqualTo(running);
        }
        assertThat(running).isEqualTo(stock(admin, product));

        // Valuation: 20 units x 7.20 = 144.00 at cost; retail 20 x 20.00.
        JsonNode valuation = api.get("/api/v1/inventory/reports/valuation?categoryId=" + cat, admin).json();
        assertThat(valuation.get("method").asString()).isEqualTo("WEIGHTED_AVERAGE");
        assertThat(dec(valuation.get("costValue"))).isEqualByComparingTo("144.00");
        assertThat(dec(valuation.get("retailValue"))).isEqualByComparingTo("400.00");

        // Cost of goods sold uses the cost at the time of sale (6.50), not today's average (7.20).
        String today = java.time.LocalDate.now().toString();
        JsonNode cogs = api.get("/api/v1/inventory/reports/cogs?from=" + today + "&to=" + today + "&categoryId=" + cat, admin).json();
        assertThat(cogs.get("unitsSold").asInt()).isEqualTo(4);
        assertThat(dec(cogs.get("revenue"))).isEqualByComparingTo("80.00");
        assertThat(dec(cogs.get("costOfGoodsSold"))).isEqualByComparingTo("26.00");
        assertThat(dec(cogs.get("grossProfit"))).isEqualByComparingTo("54.00");
        assertThat(dec(cogs.get("marginPercent"))).isEqualByComparingTo("67.50");
    }

    @Test
    void adjustmentsAreValidatedReasonedAndIdempotent() {
        String admin = adminToken();
        long product = product(admin, "10.00", "2.00", 10).get("id").asLong();

        ApiClient.Response damaged = api.post("/api/v1/inventory-movements/adjustment", Map.of(
                "productId", product, "quantity", -3, "reason", "Dropped a carton", "reasonCode", "damaged"), admin);
        assertThat(damaged.status()).as(damaged.body()).isEqualTo(201);
        assertThat(damaged.json().get("reason").asString()).isEqualTo("DAMAGED");
        assertThat(damaged.json().get("balanceAfter").asInt()).isEqualTo(7);
        assertThat(damaged.json().get("unitCost").decimalValue()).isEqualByComparingTo("2");
        assertThat(damaged.json().get("performedBy").asString()).isEqualTo(ADMIN);

        // Never below zero, and nothing changes when refused.
        ApiClient.Response tooMuch = api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", product, "quantity", -8, "reason", "typo"), admin);
        assertThat(tooMuch.status()).isEqualTo(400);
        assertThat(stock(admin, product)).isEqualTo(7);

        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", product, "quantity", -1, "reason", "x", "reasonCode", "BOGUS"), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", product, "quantity", 0, "reason", "x"), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", product, "quantity", -1), admin).status()).isEqualTo(400); // no reason

        // A retried request with the same key adjusts once.
        Map<String, Object> body = Map.of("productId", product, "quantity", -2, "reason", "Expired",
                "reasonCode", "EXPIRED", "requestId", requestId());
        ApiClient.Response first = api.post("/api/v1/inventory-movements/adjustment", body, admin);
        ApiClient.Response retry = api.post("/api/v1/inventory-movements/adjustment", body, admin);
        assertThat(retry.json().get("id").asLong()).isEqualTo(first.json().get("id").asLong());
        assertThat(stock(admin, product)).isEqualTo(5);

        // The same for restocks; reusing the key for another operation is a conflict.
        Map<String, Object> restock = Map.of("productId", product, "quantity", 5, "unitCost", "3.00", "requestId", requestId());
        api.post("/api/v1/inventory-movements/restock", restock, admin);
        api.post("/api/v1/inventory-movements/restock", restock, admin);
        assertThat(stock(admin, product)).isEqualTo(10);
        assertThat(api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1,
                "reason", "x", "requestId", restock.get("requestId")), admin).status()).isEqualTo(409);

        // The product update route cannot be used to bypass the ledger: it records an adjustment too.
        api.put("/api/v1/products/" + product, Map.of("stockQuantity", 8), admin);
        JsonNode viaUpdate = last(movements(admin, product));
        assertThat(viaUpdate.get("movementType").asString()).isEqualTo("ADJUSTMENT");
        assertThat(viaUpdate.get("quantity").asInt()).isEqualTo(-2);
        assertThat(viaUpdate.get("balanceAfter").asInt()).isEqualTo(8);
    }

    @Test
    void voidingRestoresStockAtTheOriginalCostAndIsAudited() {
        String admin = adminToken();
        long product = product(admin, "10.00", "5.00", 10).get("id").asLong();

        JsonNode sale = sell(admin, product, 3).json(); // unpaid: stock reserved
        assertThat(stock(admin, product)).isEqualTo(7);

        // The average moves after the sale: (7 x 5 + 5 x 11) / 12 = 7.50.
        api.post("/api/v1/inventory-movements/restock", Map.of("productId", product, "quantity", 5, "unitCost", "11.00"), admin);
        assertThat(averageCost(admin, product)).isEqualByComparingTo("7.5");

        ApiClient.Response voided = api.post("/api/v1/sales/" + sale.get("id").asLong() + "/void", Map.of("reason", "Customer left"), admin);
        assertThat(voided.status()).as(voided.body()).isEqualTo(200);
        assertThat(voided.json().get("saleStatus").asString()).isEqualTo("VOIDED");
        assertThat(voided.json().get("voidReason").asString()).isEqualTo("Customer left");
        assertThat(voided.json().get("voidedBy").asString()).isEqualTo(ADMIN);
        assertThat(voided.json().get("voidedAt").isNull()).isFalse();

        assertThat(stock(admin, product)).isEqualTo(15);
        JsonNode movement = last(movements(admin, product));
        assertThat(movement.get("movementType").asString()).isEqualTo("SALE_VOID");
        assertThat(movement.get("unitCost").decimalValue()).isEqualByComparingTo("5"); // the cost it left at
        // (12 x 7.5 + 3 x 5) / 15 = 7.00
        assertThat(averageCost(admin, product)).isEqualByComparingTo("7");

        // Voiding twice cannot restore stock twice.
        assertThat(api.post("/api/v1/sales/" + sale.get("id").asLong() + "/void", null, admin).status()).isEqualTo(400);
        assertThat(stock(admin, product)).isEqualTo(15);
    }

    @Test
    void customerReturnsSeparateSaleableFromDamagedGoods() {
        String admin = adminToken();
        long product = product(admin, "10.00", "5.00", 10).get("id").asLong();
        long cash = paymentMethodId("CASH", admin);

        JsonNode sale = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product, "quantity", 4)),
                "payments", List.of(Map.of("paymentMethodId", cash))), admin).json();
        long itemId = sale.get("items").get(0).get("id").asLong();
        assertThat(stock(admin, product)).isEqualTo(6);

        // Three come back: two resaleable, one damaged.
        ApiClient.Response refund = api.post("/api/v1/sales/" + sale.get("id").asLong() + "/refund", Map.of(
                "reason", "Not wanted", "items", List.of(
                        Map.of("saleItemId", itemId, "quantity", 2, "condition", "SALEABLE"),
                        Map.of("saleItemId", itemId, "quantity", 1, "condition", "DAMAGED"))), admin);
        assertThat(refund.status()).as(refund.body()).isEqualTo(200);

        assertThat(stock(admin, product)).isEqualTo(8); // 6 + 3 returned - 1 written off
        JsonNode ledger = movements(admin, product);
        JsonNode writeOff = last(ledger);
        JsonNode returned = ledger.get(ledger.size() - 2);
        assertThat(returned.get("movementType").asString()).isEqualTo("RETURN");
        assertThat(returned.get("quantity").asInt()).isEqualTo(3);
        assertThat(writeOff.get("movementType").asString()).isEqualTo("ADJUSTMENT");
        assertThat(writeOff.get("reason").asString()).isEqualTo("DAMAGED_RETURN");
        assertThat(writeOff.get("quantity").asInt()).isEqualTo(-1);

        // Marking more damaged than refunded, or an unknown condition, is refused.
        JsonNode sale2 = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product, "quantity", 2)),
                "payments", List.of(Map.of("paymentMethodId", cash))), admin).json();
        long item2 = sale2.get("items").get(0).get("id").asLong();
        assertThat(api.post("/api/v1/sales/" + sale2.get("id").asLong() + "/refund", Map.of("items",
                List.of(Map.of("saleItemId", item2, "quantity", 1, "condition", "BROKEN"))), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/sales/" + sale2.get("id").asLong() + "/refund", Map.of("saleItemId", 1,
                "damagedSaleItemIds", List.of(item2), "saleItemIds", List.of(item2)), admin).status()).isEqualTo(200);
        assertThat(stock(admin, product)).isEqualTo(6); // 8 - 2 sold + 2 returned - 2 written off
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        String admin = adminToken();
        long product = product(admin, "10.00", "1.00", 3).get("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        int created = 0;
        try {
            List<Callable<ApiClient.Response>> calls = IntStream.range(0, 8)
                    .<Callable<ApiClient.Response>>mapToObj(i -> () -> sell(admin, product, 1)).toList();
            for (Future<ApiClient.Response> f : pool.invokeAll(calls)) {
                ApiClient.Response response = f.get();
                assertThat(response.status()).isIn(201, 400);
                if (response.status() == 201) {
                    created++;
                } else {
                    assertThat(response.json().get("message").asString()).contains("Insufficient stock");
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(created).isEqualTo(3);
        assertThat(stock(admin, product)).isZero();
        JsonNode ledger = movements(admin, product);
        for (JsonNode movement : ledger) {
            assertThat(movement.get("balanceAfter").asInt()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void productCatalogueSearchSortingAndPagination() {
        String admin = adminToken();
        long cat = category(admin);
        String tag = "Zq" + shortId();
        for (int i = 1; i <= 5; i++) {
            api.post("/api/v1/products", Map.of("name", tag + " item " + i, "sku", "S-" + tag + i, "price", i * 10,
                    "stockQuantity", i == 1 ? 0 : i * 10, "categoryId", cat, "reorderLevel", 15,
                    "barcode", i <= 2 ? tag.toUpperCase() + "BC" + i : ""), admin);
        }

        JsonNode page = api.get("/api/v1/products/search?q=" + tag + "&sort=price,desc&size=2&page=0", admin).json();
        assertThat(page.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("content").size()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("name").asString()).isEqualTo(tag + " item 5");
        JsonNode last = api.get("/api/v1/products/search?q=" + tag + "&sort=price,desc&size=2&page=2", admin).json();
        assertThat(last.get("content").size()).isEqualTo(1);
        assertThat(last.get("content").get(0).get("name").asString()).isEqualTo(tag + " item 1");

        assertThat(api.get("/api/v1/products/search?categoryId=" + cat + "&outOfStock=true", admin).json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/products/search?categoryId=" + cat + "&lowStock=true", admin).json().get("totalElements").asInt()).isEqualTo(1); // only the empty one is at or below the reorder level of 15
        assertThat(api.get("/api/v1/products/search?categoryId=" + cat + "&hasBarcode=true", admin).json().get("totalElements").asInt()).isEqualTo(2);
        assertThat(api.get("/api/v1/products/search?q=" + tag.toLowerCase() + "bc1", admin).json().get("totalElements").asInt()).isEqualTo(1); // by barcode
        assertThat(api.get("/api/v1/products/search?categoryId=" + cat + "&minPrice=20&maxPrice=40", admin).json().get("totalElements").asInt()).isEqualTo(3);

        assertThat(api.get("/api/v1/products/search?sort=password", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/products/search?sort=price,sideways", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/products/search?size=1000", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/products/search?page=-1", admin).status()).isEqualTo(400);

        // The unpaginated list used by existing clients still works.
        assertThat(api.get("/api/v1/products", admin).json().isArray()).isTrue();

        // Stock levels (inventory view) filter by status.
        JsonNode levels = api.get("/api/v1/inventory/stock?q=" + tag + "&status=OUT_OF_STOCK", admin).json();
        assertThat(levels.get("totalElements").asInt()).isEqualTo(1);
        assertThat(levels.get("content").get(0).get("status").asString()).isEqualTo("OUT_OF_STOCK");
        assertThat(api.get("/api/v1/inventory/stock?q=" + tag + "&status=LOW_STOCK", admin).json().get("totalElements").asInt()).isEqualTo(0);
        assertThat(api.get("/api/v1/inventory/stock?status=NOPE", admin).status()).isEqualTo(400);
        JsonNode summary = api.get("/api/v1/inventory/stock/summary?categoryId=" + cat, admin).json();
        assertThat(summary.get("trackedProducts").asInt()).isEqualTo(5);
        assertThat(summary.get("outOfStock").asInt()).isEqualTo(1);
    }

    @Test
    void archivingDeletionAndUnits() {
        String admin = adminToken();
        JsonNode sold = product(admin, "10.00", "1.00", 5);
        long soldId = sold.get("id").asLong();
        sell(admin, soldId, 1);

        // A product with history is archived, never deleted.
        assertThat(api.delete("/api/v1/products/" + soldId, admin).status()).isEqualTo(204);
        JsonNode archived = getProduct(admin, soldId);
        assertThat(archived.get("active").asBoolean()).isFalse();
        assertThat(sell(admin, soldId, 1).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/products/search?active=false&q=" + sold.get("name").asString().replace(" ", "%20"), admin)
                .json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.post("/api/v1/products/" + soldId + "/restore", null, admin).json().get("active").asBoolean()).isTrue();
        assertThat(api.post("/api/v1/products/" + soldId + "/archive", null, admin).json().get("active").asBoolean()).isFalse();

        // Archived products cannot be ordered; one only referenced by an order is archived, not deleted.
        long supplier = supplier(admin);
        assertThat(api.post("/api/v1/purchase-orders", Map.of("supplierId", supplier, "lines",
                List.of(Map.of("productId", soldId, "quantity", 1))), admin).status()).isEqualTo(400);
        long ordered = product(admin, "10.00", "1.00", 0).get("id").asLong();
        purchaseOrder(admin, supplier, false, ordered, 5, "1.00");
        assertThat(api.delete("/api/v1/products/" + ordered, admin).status()).isEqualTo(204);
        assertThat(getProduct(admin, ordered).get("active").asBoolean()).isFalse();

        // A product that was never used is really deleted.
        long unused = product(admin, "10.00", "1.00", 0).get("id").asLong();
        assertThat(api.delete("/api/v1/products/" + unused, admin).status()).isEqualTo(204);
        assertThat(api.get("/api/v1/products/" + unused, admin).status()).isEqualTo(404);

        // Units of measurement and descriptions.
        String code = "U" + shortId().substring(0, 4).toUpperCase();
        ApiClient.Response unit = api.post("/api/v1/units", Map.of("code", code, "name", "Test unit"), admin);
        assertThat(unit.status()).as(unit.body()).isEqualTo(201);
        long unitId = unit.json().get("id").asLong();
        assertThat(api.post("/api/v1/units", Map.of("code", code.toLowerCase(), "name", "dup"), admin).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/units", Map.of("code", "bad code", "name", "x"), admin).status()).isEqualTo(400);

        ApiClient.Response withUnit = api.put("/api/v1/products/" + soldId, Map.of("unitId", unitId, "description", "Fresh and local"), admin);
        assertThat(withUnit.json().get("unitCode").asString()).isEqualTo(code);
        assertThat(withUnit.json().get("description").asString()).isEqualTo("Fresh and local");
        assertThat(api.delete("/api/v1/units/" + unitId, admin).status()).isEqualTo(409); // in use
        assertThat(api.put("/api/v1/products/" + soldId, Map.of("unitId", 99999999), admin).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/units", admin).json().toString()).contains("\"code\":\"PCS\"");
    }

    @Test
    void priceChangesNeedTheirOwnPermissionAndAreAudited() {
        String admin = adminToken();
        long product = product(admin, "10.00", "4.00", 5).get("id").asLong();

        // A role that can edit products but not prices.
        RoleEntity role = roleRepository.findByName("STOCKER").orElseGet(() -> {
            Set<com.devkanyanta.pos.user.role_permissions.PermissionEntity> permissions = new HashSet<>();
            for (String name : List.of(PermissionConstants.PRODUCT_VIEW, PermissionConstants.PRODUCT_UPDATE)) {
                permissions.add(permissionRepository.findByName(name).orElseThrow());
            }
            return roleRepository.save(RoleEntity.builder().name("STOCKER").permissions(permissions).build());
        });
        String username = "stocker-" + shortId();
        api.post("/api/v1/users", Map.of("firstName", "S", "lastName", "T", "email", username + "@example.com",
                "username", username, "password", "secret-123", "roleIds", List.of(role.getId())), admin);
        String stocker = login(username, "secret-123");

        assertThat(api.put("/api/v1/products/" + product, Map.of("name", "Renamed " + shortId()), stocker).status()).isEqualTo(200);
        assertThat(api.put("/api/v1/products/" + product, Map.of("price", "12.00"), stocker).status()).isEqualTo(403);
        assertThat(api.put("/api/v1/products/" + product, Map.of("costPrice", "9.00"), stocker).status()).isEqualTo(403);
        assertThat(api.put("/api/v1/products/" + product, Map.of("stockQuantity", 50), stocker).status()).isEqualTo(403);
        assertThat(getProduct(admin, product).get("price").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(stock(admin, product)).isEqualTo(5);

        assertThat(api.put("/api/v1/products/" + product, Map.of("price", "12.00", "costPrice", "5.00"), admin).status()).isEqualTo(200);
        assertThat(api.put("/api/v1/products/" + product, Map.of("price", "12.00"), admin).status()).isEqualTo(200); // unchanged

        JsonNode history = api.get("/api/v1/products/" + product + "/price-history", admin).json();
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("oldPrice").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(history.get(0).get("newPrice").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(history.get(0).get("oldCostPrice").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(history.get(0).get("newCostPrice").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(history.get(0).get("changedBy").asString()).isEqualTo(ADMIN);
    }

    @Test
    void rolePermissionsProtectInventoryOperations() {
        String admin = adminToken();
        String cashier = userToken("CASHIER");
        String manager = userToken("MANAGER");
        long product = product(admin, "10.00", "1.00", 5).get("id").asLong();
        long supplier = supplier(admin);

        // Cashiers see stock but cannot change or buy it.
        assertThat(api.get("/api/v1/inventory/stock", cashier).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/inventory/movements", cashier).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1, "reason", "x"), cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/inventory-movements/restock", Map.of("productId", product, "quantity", 1), cashier).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/suppliers", cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/suppliers", Map.of("name", "Nope"), cashier).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/purchase-orders", cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/purchase-orders", Map.of("supplierId", supplier), cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/supplier-returns", Map.of("requestId", requestId()), cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/stock-counts", null, cashier).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/inventory/reports/valuation", cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/products/" + product + "/archive", null, cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/units", Map.of("code", "ZZ", "name", "x"), cashier).status()).isEqualTo(403);

        // Managers run purchasing and counts.
        assertThat(api.get("/api/v1/suppliers", manager).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/suppliers", Map.of("name", "Mgr supplier " + shortId()), manager).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/stock-counts", null, manager).status()).isEqualTo(201);
        assertThat(api.get("/api/v1/inventory/reports/valuation", manager).status()).isEqualTo(200);

        // Not signed in.
        assertThat(api.get("/api/v1/inventory/stock", null).status()).isIn(401, 403);
        assertThat(api.get("/api/v1/purchase-orders", null).status()).isIn(401, 403);
    }

    @Test
    void movementHistoryIsFilterableAndPaginated() {
        String admin = adminToken();
        long product = product(admin, "10.00", "1.00", 10).get("id").asLong();
        api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1, "reason", "a", "reasonCode", "LOST"), admin);
        api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1, "reason", "b", "reasonCode", "DAMAGED"), admin);
        sell(admin, product, 2);

        JsonNode all = api.get("/api/v1/inventory/movements?productId=" + product + "&size=2&page=0", admin).json();
        assertThat(all.get("totalElements").asInt()).isEqualTo(4); // opening, 2 adjustments, 1 sale
        assertThat(all.get("totalPages").asInt()).isEqualTo(2);
        assertThat(all.get("content").get(0).get("movementType").asString()).isEqualTo("SALE"); // newest first

        assertThat(api.get("/api/v1/inventory/movements?productId=" + product + "&type=ADJUSTMENT", admin).json().get("totalElements").asInt()).isEqualTo(2);
        assertThat(api.get("/api/v1/inventory/movements?productId=" + product + "&reason=lost", admin).json().get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/inventory/movements?productId=" + product + "&referenceType=SALE", admin).json().get("totalElements").asInt()).isEqualTo(1);
        String today = java.time.LocalDate.now().toString();
        assertThat(api.get("/api/v1/inventory/movements?productId=" + product + "&from=" + today + "&to=" + today, admin).json().get("totalElements").asInt()).isEqualTo(4);
        assertThat(api.get("/api/v1/inventory/movements?productId=" + product + "&from=2000-01-01&to=2000-01-02", admin).json().get("totalElements").asInt()).isZero();
        assertThat(api.get("/api/v1/inventory/movements?type=NOPE", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/inventory/movements?from=2026-02-01&to=2026-01-01", admin).status()).isEqualTo(400);

        // Existing endpoints keep working.
        assertThat(api.get("/api/v1/inventory-movements/product/" + product, admin).json().isArray()).isTrue();
    }

    @Test
    void migrationBackfillsCostingForDataFromBeforeCosting() throws Exception {
        String admin = adminToken();
        long product = product(admin, "10.00", "6.00", 10).get("id").asLong();
        long saleId = sell(admin, product, 2).json().get("id").asLong();
        api.post("/api/v1/inventory-movements/adjustment", Map.of("productId", product, "quantity", -1, "reason", "x"), admin);

        // Make the rows look like they were written before costing existed.
        jdbc.update("UPDATE products SET average_cost = NULL WHERE id = ?", product);
        jdbc.update("UPDATE sale_items SET unit_cost = NULL WHERE sale_id = ?", saleId);
        jdbc.update("UPDATE inventory_movements SET balance_after = NULL, unit_cost = NULL WHERE product_id = ?", product);
        jdbc.update("DELETE FROM schema_migrations WHERE version = ?", "2026.10.07-inventory-costing");

        migrationRunner.run(null);

        assertThat(jdbc.queryForObject("SELECT average_cost FROM products WHERE id = ?", java.math.BigDecimal.class, product))
                .isEqualByComparingTo("6.00");
        assertThat(jdbc.queryForObject("SELECT unit_cost FROM sale_items WHERE sale_id = ?", java.math.BigDecimal.class, saleId))
                .isEqualByComparingTo("6.00");
        // Running balance: opening 10, sale -2, adjustment -1.
        List<Integer> balances = jdbc.queryForList(
                "SELECT balance_after FROM inventory_movements WHERE product_id = ? ORDER BY id", Integer.class, product);
        assertThat(balances).containsExactly(10, 8, 7);

        // The product keeps working afterwards.
        assertThat(sell(admin, product, 1).status()).isEqualTo(201);
        assertThat(stock(admin, product)).isEqualTo(6);
    }
}
