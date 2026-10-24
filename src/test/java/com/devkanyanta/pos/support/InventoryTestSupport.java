package com.devkanyanta.pos.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Helpers for tests that exercise inventory, purchasing and costing. */
public abstract class InventoryTestSupport extends IntegrationTestBase {

    protected static String requestId() {
        return "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    protected long category(String token) {
        ApiClient.Response response = api.post("/api/v1/categories",
                Map.of("name", "Cat " + shortId(), "description", "test"), token);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json().get("id").asLong();
    }

    /** A ZRA-mapped, inventory-tracked product with a cost, unique barcode and unique name. */
    protected JsonNode product(String token, Long categoryId, String price, String cost, int stock) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "name", "Item " + shortId(), "sku", "SKU-" + shortId(), "price", price, "costPrice", cost,
                "stockQuantity", stock, "zraTaxType", "A", "zraItemClassCode", "50202300",
                "zraItemTypeCode", "2", "zraQuantityUnitCode", "U", "zraPackagingUnitCode", "NT"));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        ApiClient.Response response = api.post("/api/v1/products", body, token);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json();
    }

    protected JsonNode product(String token, String price, String cost, int stock) {
        return product(token, null, price, cost, stock);
    }

    protected JsonNode getProduct(String token, long id) {
        return api.get("/api/v1/products/" + id, token).json();
    }

    protected int stock(String token, long id) {
        return getProduct(token, id).get("stockQuantity").asInt();
    }

    protected BigDecimal averageCost(String token, long id) {
        return getProduct(token, id).get("averageCost").decimalValue();
    }

    protected long supplier(String token) {
        ApiClient.Response response = api.post("/api/v1/suppliers", Map.of(
                "name", "Supplier " + shortId(), "contactPerson", "Joe", "phone", "0977111222",
                "email", "sales@supplier.example"), token);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json().get("id").asLong();
    }

    /** Creates a purchase order for {@code productId -> {quantity, unitCost}} pairs; placed unless draft. */
    protected JsonNode purchaseOrder(String token, long supplierId, boolean place, Object... lines) {
        java.util.ArrayList<Map<String, Object>> lineList = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i += 3) {
            lineList.add(Map.of("productId", lines[i], "quantity", lines[i + 1], "unitCost", lines[i + 2]));
        }
        ApiClient.Response response = api.post("/api/v1/purchase-orders", Map.of(
                "supplierId", supplierId, "lines", lineList, "order", place), token);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json();
    }

    protected ApiClient.Response receive(String token, long poId, Object... productQtyCost) {
        java.util.ArrayList<Map<String, Object>> lines = new java.util.ArrayList<>();
        for (int i = 0; i < productQtyCost.length; i += 2) {
            lines.add(Map.of("productId", productQtyCost[i], "quantity", productQtyCost[i + 1]));
        }
        return api.post("/api/v1/purchase-orders/" + poId + "/receipts",
                Map.of("requestId", requestId(), "lines", lines), token);
    }

    protected ApiClient.Response sell(String token, long productId, int quantity) {
        return api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", quantity))), token);
    }

    protected JsonNode movements(String token, long productId) {
        ApiClient.Response response = api.get("/api/v1/inventory/movements?productId=" + productId + "&size=100&sort=createdAt,asc", token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json().get("content");
    }

    protected static JsonNode last(JsonNode array) {
        return array.get(array.size() - 1);
    }
}
