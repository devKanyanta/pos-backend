package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/** Offline-first sync: idempotent batch upload, conflict rules, catalogue pull and status. */
class OfflineSyncIntegrationTest extends IntegrationTestBase {

    private static final ZoneId LUSAKA = ZoneId.of("Africa/Lusaka");

    @Test
    void offlineSalesSyncIdempotentlyWithConflictFlags() {

        String admin = adminToken();
        String cashier = userToken("CASHIER");
        String deviceId = "android-" + shortId();

        JsonNode soap = createProduct(admin, "Soap " + shortId(), "8.00", 3, "A");
        long soapId = soap.get("id").asLong();
        long cash = paymentMethodId("CASH", admin);
        long airtel = paymentMethodId("AIRTEL", admin);

        ApiClient.Response registered = api.post("/api/v1/sync/devices/register",
                Map.of("deviceId", deviceId, "name", "Till 1", "model", "Tecno Spark", "appVersion", "1.0.0"), cashier);
        assertThat(registered.status()).as(registered.body()).isEqualTo(200);

        LocalDateTime soldAt = LocalDateTime.now(LUSAKA).minusHours(3).withNano(0);
        String normalId = "sale-" + shortId() + shortId();
        String conflictId = "sale-" + shortId() + shortId();
        String badId = "sale-" + shortId() + shortId();

        Map<String, Object> normal = Map.of(
                "clientSaleId", normalId,
                "clientReceiptNumber", "T1-0001",
                "soldAt", soldAt.toString(),
                "expectedTotal", "18.56",
                "items", List.of(Map.of("productId", soapId, "quantity", 2, "unitPrice", "8.00")),
                "payments", List.of(Map.of("paymentMethodId", airtel, "amount", "18.56", "transactionReference", "AM" + shortId())));

        // Power cut: device sold 5 more soaps than the server thinks are in stock, at an old price,
        // and its total was computed differently.
        Map<String, Object> conflicting = Map.of(
                "clientSaleId", conflictId,
                "soldAt", soldAt.plusMinutes(5).toString(),
                "expectedTotal", "40.00",
                "items", List.of(Map.of("productId", soapId, "quantity", 5, "unitPrice", "7.50")),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "50.00")));

        Map<String, Object> invalid = Map.of(
                "clientSaleId", badId,
                "soldAt", LocalDateTime.now(LUSAKA).plusDays(2).toString(),
                "items", List.of(Map.of("productId", soapId, "quantity", 1)));

        Map<String, Object> batch = Map.of("deviceId", deviceId, "pendingQueueSize", 0,
                "sales", List.of(normal, conflicting, invalid));

        ApiClient.Response first = api.post("/api/v1/sync/sales", batch, cashier);
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        JsonNode result = first.json();
        assertThat(result.get("created").asInt()).isEqualTo(2);
        assertThat(result.get("rejected").asInt()).isEqualTo(1);

        JsonNode normalResult = result.get("results").get(0);
        assertThat(normalResult.get("status").asString()).isEqualTo("CREATED");
        assertThat(normalResult.get("paymentStatus").asString()).isEqualTo("PAID");
        assertThat(normalResult.get("receiptNumber").asString()).startsWith("RCT-");
        assertThat(normalResult.get("conflicts").size()).isZero();

        JsonNode conflictResult = result.get("results").get(1);
        assertThat(conflictResult.get("status").asString()).isEqualTo("CREATED");
        assertThat(conflictResult.get("conflicts").toString())
                .contains("NEGATIVE_STOCK").contains("PRICE_MISMATCH").contains("TOTAL_MISMATCH");
        // Device price wins: 5 x 7.50 = 37.50 + 16% = 43.50
        assertThat(dec(conflictResult.get("totalAmount"))).isEqualByComparingTo("43.50");

        JsonNode invalidResult = result.get("results").get(2);
        assertThat(invalidResult.get("status").asString()).isEqualTo("REJECTED");
        assertThat(invalidResult.get("error").asString()).contains("future");

        // Stock went negative (3 - 2 - 5) and both sales were recorded with the device time.
        assertThat(api.get("/api/v1/products/" + soapId, admin).json().get("stockQuantity").asInt()).isEqualTo(-4);
        JsonNode synced = api.get("/api/v1/sales/" + normalResult.get("saleId").asLong(), admin).json();
        assertThat(synced.get("source").asString()).isEqualTo("OFFLINE_SYNC");
        assertThat(synced.get("deviceId").asString()).isEqualTo(deviceId);
        assertThat(synced.get("clientReceiptNumber").asString()).isEqualTo("T1-0001");
        assertThat(LocalDateTime.parse(synced.get("soldAt").asString())).isEqualTo(soldAt);
        assertThat(synced.get("payments").get(0).get("paidAt").asString()).startsWith(soldAt.toString().substring(0, 16));

        // Retrying the same batch after a dropped connection creates nothing new.
        ApiClient.Response second = api.post("/api/v1/sync/sales", batch, cashier);
        assertThat(second.json().get("created").asInt()).isZero();
        assertThat(second.json().get("duplicates").asInt()).isEqualTo(2);
        assertThat(second.json().get("results").get(0).get("saleId").asLong())
                .isEqualTo(normalResult.get("saleId").asLong());
        assertThat(api.get("/api/v1/products/" + soapId, admin).json().get("stockQuantity").asInt()).isEqualTo(-4);

        // The conflicting sale is flagged for a manager.
        long conflictSaleId = conflictResult.get("saleId").asLong();
        ApiClient.Response review = api.get("/api/v1/sales/needs-review", admin);
        assertThat(review.status()).isEqualTo(200);
        assertThat(review.body()).contains("\"id\":" + conflictSaleId);
        assertThat(api.get("/api/v1/sales/needs-review", cashier).status()).isEqualTo(403);

        ApiClient.Response reviewed = api.post("/api/v1/sales/" + conflictSaleId + "/review", null, admin);
        assertThat(reviewed.status()).isEqualTo(200);
        assertThat(reviewed.json().get("needsReview").asBoolean()).isFalse();

        // Device registry reflects the sync.
        JsonNode status = api.get("/api/v1/sync/status?deviceId=" + deviceId, cashier).json();
        assertThat(status.get("deviceRegistered").asBoolean()).isTrue();
        assertThat(status.get("lastSyncAt").isNull()).isFalse();
        assertThat(status.get("pendingQueueSize").asInt()).isZero();
    }

    @Test
    void batchRejectsDuplicateIdsWithinTheBatchAndUnknownProductsAreRecordedFromSnapshot() {

        String admin = adminToken();
        String deviceId = "android-" + shortId();
        long cash = paymentMethodId("CASH", admin);
        LocalDateTime soldAt = LocalDateTime.now(LUSAKA).minusMinutes(30).withNano(0);
        String id = "dup-" + shortId() + shortId();

        Map<String, Object> unknownProductSale = Map.of(
                "clientSaleId", id,
                "soldAt", soldAt.toString(),
                "items", List.of(Map.of("productId", 999_999, "productName", "Deleted item", "quantity", 1,
                        "unitPrice", "10.00", "zraTaxType", "D")),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "10.00")));

        ApiClient.Response response = api.post("/api/v1/sync/sales", Map.of("deviceId", deviceId,
                "sales", List.of(unknownProductSale, unknownProductSale)), admin);
        assertThat(response.status()).as(response.body()).isEqualTo(200);

        JsonNode results = response.json().get("results");
        assertThat(results.get(0).get("status").asString()).isEqualTo("CREATED");
        assertThat(results.get(0).get("conflicts").toString()).contains("UNKNOWN_PRODUCT");
        assertThat(dec(results.get(0).get("totalAmount"))).isEqualByComparingTo("10.00");
        assertThat(results.get(1).get("status").asString()).isEqualTo("REJECTED");
        assertThat(results.get(1).get("error").asString()).contains("more than once");

        // Missing clientSaleId is rejected per sale, not for the whole batch.
        ApiClient.Response missingId = api.post("/api/v1/sync/sales", Map.of("deviceId", deviceId,
                "sales", List.of(Map.of("soldAt", soldAt.toString(),
                        "items", List.of(Map.of("productId", 1, "quantity", 1))))), admin);
        assertThat(missingId.json().get("rejected").asInt()).isEqualTo(1);

        // A deactivated device can no longer sync.
        assertThat(api.post("/api/v1/sync/devices/" + deviceId + "/deactivate", null, admin).status()).isEqualTo(200);
        ApiClient.Response blocked = api.post("/api/v1/sync/sales", Map.of("deviceId", deviceId,
                "sales", List.of(unknownProductSale)), admin);
        assertThat(blocked.status()).isEqualTo(400);
        assertThat(blocked.json().get("message").asString()).contains("deactivated");
    }

    @Test
    void catalogueDeltaReturnsOnlyChangedProductsAndAllIds() {

        String admin = adminToken();
        String cashier = userToken("CASHIER");

        JsonNode full = api.get("/api/v1/sync/catalog", cashier).json();
        assertThat(full.get("full").asBoolean()).isTrue();
        assertThat(full.get("paymentMethods").toString()).contains("ZAMTEL");
        String since = full.get("serverTime").asString();

        sleep(1100);
        JsonNode created = createProduct(admin, "Rice 5kg " + shortId(), "120.00", 10, "C3");

        JsonNode delta = api.get("/api/v1/sync/catalog?since=" + since, cashier).json();
        assertThat(delta.get("full").asBoolean()).isFalse();
        assertThat(delta.get("products").toString()).contains(created.get("name").asString());
        assertThat(delta.get("products").size()).isLessThan(full.get("products").size() + 1);
        assertThat(delta.get("productIds").toString()).contains(String.valueOf(created.get("id").asLong()));

        ApiClient.Response heartbeat = api.post("/api/v1/sync/heartbeat",
                Map.of("deviceId", "android-" + shortId(), "pendingQueueSize", 7, "online", false), cashier);
        assertThat(heartbeat.status()).isEqualTo(200);
        assertThat(heartbeat.json().get("pendingQueueSize").asInt()).isEqualTo(7);

        JsonNode dashboard = api.get("/api/v1/dashboard", admin).json();
        assertThat(dashboard.get("deviceQueuedTransactions").asLong()).isGreaterThanOrEqualTo(7);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
