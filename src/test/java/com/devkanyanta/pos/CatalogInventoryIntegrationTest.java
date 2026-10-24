package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/** Catalogue: ZRA item mapping validation, CSV import, low-stock alerts and stock movements. */
class CatalogInventoryIntegrationTest extends IntegrationTestBase {

    @Test
    void productZraMappingIsValidated() {

        String admin = adminToken();

        // Tax type fixes the VAT rate.
        ApiClient.Response mismatch = api.post("/api/v1/products", Map.of(
                "name", "Juice " + shortId(), "price", "10.00", "stockQuantity", 1,
                "zraTaxType", "A", "vatRate", "0"), admin);
        assertThat(mismatch.status()).isEqualTo(400);
        assertThat(mismatch.json().get("message").asString()).contains("does not match ZRA tax type A");

        ApiClient.Response unknownType = api.post("/api/v1/products", Map.of(
                "name", "Juice " + shortId(), "price", "10.00", "stockQuantity", 1, "zraTaxType", "Z"), admin);
        assertThat(unknownType.status()).isEqualTo(400);

        ApiClient.Response badItemType = api.post("/api/v1/products", Map.of(
                "name", "Juice " + shortId(), "price", "10.00", "stockQuantity", 1, "zraItemTypeCode", "7"), admin);
        assertThat(badItemType.status()).isEqualTo(400);

        // Legacy request with only a VAT rate still works: 16% maps to A, defaults fill units.
        ApiClient.Response legacy = api.post("/api/v1/products", Map.of(
                "name", "Juice " + shortId(), "price", "10.00", "stockQuantity", 4, "vatRate", "16"), admin);
        assertThat(legacy.status()).as(legacy.body()).isEqualTo(201);
        JsonNode product = legacy.json();
        assertThat(product.get("zraTaxType").asString()).isEqualTo("A");
        assertThat(product.get("zraQuantityUnitCode").asString()).isEqualTo("U");
        assertThat(product.get("zraOriginCountryCode").asString()).isEqualTo("ZM");
        assertThat(product.get("zraMapped").asBoolean()).isFalse(); // no item class yet
        assertThat(product.get("lowStock").asBoolean()).isTrue();   // 4 <= default threshold 5

        // ZRA is enabled in tests, so an unmapped product cannot be sold online.
        ApiClient.Response sale = api.post("/api/v1/sales", Map.of(
                "items", java.util.List.of(Map.of("productId", product.get("id").asLong(), "quantity", 1))), admin);
        assertThat(sale.status()).isEqualTo(400);
        assertThat(sale.json().get("message").asString()).contains("not mapped to ZRA");

        // Mapping it fixes that.
        ApiClient.Response mapped = api.put("/api/v1/products/" + product.get("id").asLong(),
                Map.of("zraItemClassCode", "50202300"), admin);
        assertThat(mapped.json().get("zraMapped").asBoolean()).isTrue();

        // Duplicate SKU -> 409, unknown product -> 404.
        JsonNode first = createProduct(admin, "Tea " + shortId(), "5.00", 1, "A");
        ApiClient.Response duplicate = api.post("/api/v1/products", Map.of(
                "name", "Tea copy", "sku", first.get("sku").asString(), "price", "5.00", "stockQuantity", 1), admin);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(api.get("/api/v1/products/99999999", admin).status()).isEqualTo(404);

        ApiClient.Response taxTypes = api.get("/api/v1/zra/tax-types", admin);
        assertThat(taxTypes.body()).contains("\"code\":\"C3\"").contains("\"code\":\"RVAT\"");
    }

    @Test
    void csvImportCreatesUpdatesAndReportsRowErrors() {

        String admin = adminToken();
        String suffix = shortId();
        String category = "Groceries " + suffix;

        String csv = "Name,SKU,Barcode,Price,Cost,Qty,Tax Type,Category,Reorder Level,Item Class Code,Unit\n"
                + "\"Sugar, white 2kg\",SUG-" + suffix + ",6001" + suffix + ",K 45.50,38,24,A," + category + ",10,50161509,KG\n"
                + "Salt 1kg,SAL-" + suffix + ",,12,9,3,A," + category + ",,50171551,U\n"
                + "Broken row,BRK-" + suffix + ",,abc,1,1,A,,,,\n"
                + "Exempt seed,SEED-" + suffix + ",,30,20,5,D,,,10151500,U\n";

        // Dry run validates without saving.
        ApiClient.Response dryRun = api.upload("/api/v1/products/import?dryRun=true", "file", "catalogue.csv", csv, admin);
        assertThat(dryRun.status()).as(dryRun.body()).isEqualTo(200);
        assertThat(dryRun.json().get("created").asInt()).isEqualTo(3);
        assertThat(dryRun.json().get("failed").asInt()).isEqualTo(1);
        assertThat(dryRun.json().get("rows").get(0).get("status").asString()).isEqualTo("WOULD_CREATE");
        assertThat(api.get("/api/v1/products", admin).body()).doesNotContain("SUG-" + suffix);

        ApiClient.Response imported = api.upload("/api/v1/products/import", "file", "catalogue.csv", csv, admin);
        JsonNode result = imported.json();
        assertThat(result.get("created").asInt()).isEqualTo(3);
        assertThat(result.get("failed").asInt()).isEqualTo(1);
        JsonNode broken = result.get("rows").get(2);
        assertThat(broken.get("line").asInt()).isEqualTo(4);
        assertThat(broken.get("status").asString()).isEqualTo("FAILED");
        assertThat(broken.get("errors").get(0).asString()).contains("Invalid number for 'price'");

        long sugarId = result.get("rows").get(0).get("productId").asLong();
        JsonNode sugar = api.get("/api/v1/products/" + sugarId, admin).json();
        assertThat(sugar.get("name").asString()).isEqualTo("Sugar, white 2kg");
        assertThat(dec(sugar.get("price"))).isEqualByComparingTo("45.50");
        assertThat(sugar.get("categoryName").asString()).isEqualTo(category);
        assertThat(sugar.get("zraQuantityUnitCode").asString()).isEqualTo("KG");
        assertThat(sugar.get("zraMapped").asBoolean()).isTrue();

        // Opening stock is recorded as an OPENING movement.
        JsonNode movements = api.get("/api/v1/inventory-movements/product/" + sugarId, admin).json();
        assertThat(movements.get(0).get("movementType").asString()).isEqualTo("OPENING");
        assertThat(movements.get(0).get("quantity").asInt()).isEqualTo(24);

        // Re-import as raw text/csv updates by SKU and records the stock difference.
        String update = "sku,price,stockQuantity\nSUG-" + suffix + ",47.00,30\n";
        ApiClient.Response updated = api.postRaw("/api/v1/products/import", "text/csv", update.getBytes(), admin);
        assertThat(updated.json().get("updated").asInt()).isEqualTo(1);
        JsonNode after = api.get("/api/v1/products/" + sugarId, admin).json();
        assertThat(dec(after.get("price"))).isEqualByComparingTo("47.00");
        assertThat(after.get("stockQuantity").asInt()).isEqualTo(30);
        assertThat(after.get("zraRegistered").asBoolean()).isFalse(); // price change -> re-register
        JsonNode latest = api.get("/api/v1/inventory-movements/product/" + sugarId, admin).json().get(0);
        assertThat(latest.get("movementType").asString()).isEqualTo("ADJUSTMENT");
        assertThat(latest.get("quantity").asInt()).isEqualTo(6);

        // Header without a usable column is rejected.
        ApiClient.Response noColumns = api.postRaw("/api/v1/products/import", "text/csv", "foo,bar\n1,2\n".getBytes(), admin);
        assertThat(noColumns.status()).isEqualTo(400);

        assertThat(api.get("/api/v1/products/import/template", admin).body()).startsWith("name,sku,barcode,price");
    }

    @Test
    void lowStockAlertRestockAndAdjustments() {

        String admin = adminToken();
        JsonNode flour = createProduct(admin, "Flour " + shortId(), "60.00", 12, "A");
        long id = flour.get("id").asLong();

        ApiClient.Response setLevel = api.put("/api/v1/products/" + id, Map.of("reorderLevel", 15), admin);
        assertThat(setLevel.json().get("lowStock").asBoolean()).isTrue();

        assertThat(api.get("/api/v1/products/low-stock", admin).body()).contains("\"id\":" + id);
        JsonNode dashboard = api.get("/api/v1/dashboard", admin).json();
        assertThat(dashboard.get("lowStockAlert").asBoolean()).isTrue();
        assertThat(dashboard.get("lowStockCount").asLong()).isGreaterThanOrEqualTo(1);

        ApiClient.Response restock = api.post("/api/v1/inventory-movements/restock",
                Map.of("productId", id, "quantity", 10, "unitCost", "48.00", "notes", "Delivery #123"), admin);
        assertThat(restock.status()).as(restock.body()).isEqualTo(201);
        JsonNode restocked = api.get("/api/v1/products/" + id, admin).json();
        assertThat(restocked.get("stockQuantity").asInt()).isEqualTo(22);
        assertThat(dec(restocked.get("costPrice"))).isEqualByComparingTo("48.00");
        assertThat(restocked.get("lowStock").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/products/low-stock", admin).body()).doesNotContain("\"id\":" + id + ",");

        // Adjustments need a reason and may not make stock negative.
        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", id, "quantity", -2), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", id, "quantity", -100, "reason", "Count"), admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/inventory-movements/adjustment",
                Map.of("productId", id, "quantity", -2, "reason", "Damaged bags"), admin).status()).isEqualTo(201);
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(20);

        // A product with history is deactivated rather than deleted.
        assertThat(api.delete("/api/v1/products/" + id, admin).status()).isEqualTo(204);
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("active").asBoolean()).isFalse();

        // Cashiers can see stock but not adjust it or import.
        String cashier = userToken("CASHIER");
        assertThat(api.get("/api/v1/products/low-stock", cashier).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/inventory-movements/restock",
                Map.of("productId", id, "quantity", 1), cashier).status()).isEqualTo(403);
        assertThat(api.postRaw("/api/v1/products/import", "text/csv", "name\nx\n".getBytes(), cashier).status())
                .isEqualTo(403);
    }
}
