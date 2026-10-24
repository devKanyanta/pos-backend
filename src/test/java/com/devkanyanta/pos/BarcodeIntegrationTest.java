package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.devkanyanta.pos.shared.migration.SchemaMigrationRunner;
import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/** Barcode support across the product catalogue, lookup, sales and permissions. */
class BarcodeIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SchemaMigrationRunner migrationRunner;

    /** Unique 13-digit code per call, so tests never collide on the shared test database. */
    private static String code() {
        return String.valueOf(6_000_000_000_000L + (System.nanoTime() % 900_000_000_000L));
    }

    private ApiClient.Response createWithBarcode(String token, String name, String barcode, int stock) {
        java.util.Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "name", name, "sku", "SKU-" + shortId(), "price", "10.00", "costPrice", "5.00",
                "stockQuantity", stock, "zraTaxType", "A", "zraItemClassCode", "50202300",
                "zraItemTypeCode", "2", "zraQuantityUnitCode", "U", "zraPackagingUnitCode", "NT"));
        if (barcode != null) {
            body.put("barcode", barcode);
        }
        return api.post("/api/v1/products", body, token);
    }

    private JsonNode productWithBarcode(String token, String barcode, int stock) {
        ApiClient.Response response = createWithBarcode(token, "Item " + shortId(), barcode, stock);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json();
    }

    private ApiClient.Response sell(String token, Object... items) {
        return api.post("/api/v1/sales", Map.of("items", List.of(items)), token);
    }

    @Test
    void createAndUpdateProductWithBarcode() {
        String admin = adminToken();
        String barcode = code();

        // Whitespace is trimmed and letters upper-cased before saving.
        JsonNode created = productWithBarcode(admin, "  " + barcode.toLowerCase() + " ", 5);
        assertThat(created.get("barcode").asString()).isEqualTo(barcode);

        String changed = code();
        ApiClient.Response updated = api.put("/api/v1/products/" + created.get("id").asLong(),
                Map.of("barcode", changed), admin);
        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(updated.json().get("barcode").asString()).isEqualTo(changed);

        // The old code no longer resolves; the new one does.
        assertThat(api.get("/api/v1/products/by-barcode?barcode=" + barcode, admin).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/products/by-barcode?barcode=" + changed, admin).status()).isEqualTo(200);

        // Omitting the barcode leaves it alone; an empty string clears it.
        assertThat(api.put("/api/v1/products/" + created.get("id").asLong(), Map.of("price", "11.00"), admin)
                .json().get("barcode").asString()).isEqualTo(changed);
        ApiClient.Response cleared = api.put("/api/v1/products/" + created.get("id").asLong(),
                Map.of("barcode", ""), admin);
        assertThat(cleared.json().get("barcode").isNull()).isTrue();
    }

    @Test
    void productsWithoutBarcodeAreSupported() {
        String admin = adminToken();

        JsonNode first = createWithBarcode(admin, "No code A " + shortId(), null, 3).json();
        JsonNode second = createWithBarcode(admin, "No code B " + shortId(), "", 3).json();
        assertThat(first.get("barcode").isNull()).isTrue();
        assertThat(second.get("barcode").isNull()).isTrue();

        // Still sellable by id, as before barcodes existed.
        ApiClient.Response sale = sell(admin, Map.of("productId", first.get("id").asLong(), "quantity", 1));
        assertThat(sale.status()).as(sale.body()).isEqualTo(201);
    }

    @Test
    void duplicateAndInvalidBarcodesAreRejected() {
        String admin = adminToken();
        String barcode = code();
        JsonNode original = productWithBarcode(admin, barcode, 1);

        ApiClient.Response duplicate = createWithBarcode(admin, "Copy " + shortId(), barcode, 1);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.json().get("message").asString())
                .contains(barcode).contains(original.get("name").asString());

        // Case/whitespace variants are the same barcode.
        assertThat(createWithBarcode(admin, "Copy " + shortId(), " " + barcode + " ", 1).status()).isEqualTo(409);

        // Updating a different product to take the barcode is also a conflict...
        JsonNode other = productWithBarcode(admin, code(), 1);
        assertThat(api.put("/api/v1/products/" + other.get("id").asLong(), Map.of("barcode", barcode), admin)
                .status()).isEqualTo(409);
        // ...but re-saving a product's own barcode is fine.
        assertThat(api.put("/api/v1/products/" + original.get("id").asLong(), Map.of("barcode", barcode), admin)
                .status()).isEqualTo(200);

        // UPC-A (12 digits) and the EAN-13 with a leading zero are one product.
        String upc = "0" + (10_000_000_000L + System.nanoTime() % 80_000_000_000L);
        productWithBarcode(admin, upc, 1);
        assertThat(createWithBarcode(admin, "EAN form " + shortId(), "0" + upc, 1).status()).isEqualTo(409);

        for (String bad : List.of("12", "has space 123", "12#456", "6.00123E+12", "x".repeat(65))) {
            ApiClient.Response response = createWithBarcode(admin, "Bad " + shortId(), bad, 1);
            assertThat(response.status()).as(bad).isEqualTo(400);
            assertThat(response.json().get("message").asString()).contains("Invalid barcode");
        }
    }

    @Test
    void lookupReturnsTheMatchingProduct() {
        String admin = adminToken();
        String barcode = code();
        JsonNode product = productWithBarcode(admin, barcode, 7);

        ApiClient.Response found = api.get("/api/v1/products/by-barcode?barcode=" + barcode, admin);
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.json().get("id").asLong()).isEqualTo(product.get("id").asLong());
        assertThat(found.json().get("stockQuantity").asInt()).isEqualTo(7);
        assertThat(dec(found.json().get("price"))).isEqualByComparingTo("10.00");

        // Scanner output with stray whitespace still matches.
        assertThat(api.get("/api/v1/products/by-barcode?barcode=%20" + barcode + "%20", admin).status()).isEqualTo(200);

        // Unknown -> 404 with a clear message; blank/missing -> 400.
        ApiClient.Response unknown = api.get("/api/v1/products/by-barcode?barcode=0000000000000", admin);
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.json().get("message").asString()).contains("No product found for barcode");
        assertThat(api.get("/api/v1/products/by-barcode?barcode=%20", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/products/by-barcode", admin).status()).isEqualTo(400);

        // Deactivated products are still found (so the form can warn) and are flagged inactive.
        String retired = code();
        JsonNode inactive = productWithBarcode(admin, retired, 1);
        api.put("/api/v1/products/" + inactive.get("id").asLong(), Map.of("active", false), admin);
        ApiClient.Response retiredLookup = api.get("/api/v1/products/by-barcode?barcode=" + retired, admin);
        assertThat(retiredLookup.status()).isEqualTo(200);
        assertThat(retiredLookup.json().get("active").asBoolean()).isFalse();

        // The route does not shadow GET /products/{id}.
        assertThat(api.get("/api/v1/products/" + product.get("id").asLong(), admin).status()).isEqualTo(200);
    }

    @Test
    void salesCanBeRingedUpByBarcodeAndDeductStock() {
        String admin = adminToken();
        String barcode = code();
        JsonNode product = productWithBarcode(admin, barcode, 10);
        long id = product.get("id").asLong();

        // Same product twice in one sale (once by barcode, once by id) is stock-checked together.
        ApiClient.Response response = sell(admin,
                Map.of("barcode", barcode, "quantity", 2),
                Map.of("productId", id, "barcode", barcode, "quantity", 1));
        assertThat(response.status()).as(response.body()).isEqualTo(201);

        JsonNode sale = response.json();
        // 10.00 each, 16% VAT, calculated by the server: 3 x 11.60.
        assertThat(dec(sale.get("totalAmount"))).isEqualByComparingTo("34.80");
        assertThat(dec(sale.get("vatAmount"))).isEqualByComparingTo("4.80");
        assertThat(sale.get("items").get(0).get("productId").asLong()).isEqualTo(id);

        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(7);
        JsonNode movements = api.get("/api/v1/inventory-movements/product/" + id, admin).json();
        assertThat(movements.toString()).contains("\"movementType\":\"SALE\"");
    }

    @Test
    void saleRejectsBadBarcodeLines() {
        String admin = adminToken();
        String barcode = code();
        JsonNode product = productWithBarcode(admin, barcode, 2);
        long id = product.get("id").asLong();
        String otherBarcode = code();
        long otherId = productWithBarcode(admin, otherBarcode, 2).get("id").asLong();

        ApiClient.Response unknown = sell(admin, Map.of("barcode", "0000000000000", "quantity", 1));
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.json().get("message").asString()).contains("No product found for barcode");

        ApiClient.Response mismatch = sell(admin, Map.of("productId", otherId, "barcode", barcode, "quantity", 1));
        assertThat(mismatch.status()).isEqualTo(400);
        assertThat(mismatch.json().get("message").asString()).contains("does not belong to product");

        assertThat(sell(admin, Map.of("quantity", 1)).status()).isEqualTo(400);
        assertThat(sell(admin, Map.of("barcode", barcode, "quantity", 0)).status()).isEqualTo(400);
        assertThat(sell(admin, Map.of("barcode", barcode, "quantity", -2)).status()).isEqualTo(400);

        // Exceeding stock is refused and nothing is deducted.
        ApiClient.Response oversell = sell(admin, Map.of("barcode", barcode, "quantity", 3));
        assertThat(oversell.status()).isEqualTo(400);
        assertThat(oversell.json().get("message").asString()).contains("Insufficient stock");
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(2);

        // A deactivated product cannot be sold.
        api.put("/api/v1/products/" + id, Map.of("active", false), admin);
        ApiClient.Response inactive = sell(admin, Map.of("barcode", barcode, "quantity", 1));
        assertThat(inactive.status()).isEqualTo(400);
        assertThat(inactive.json().get("message").asString()).contains("not active");
    }

    @Test
    void repeatedSubmissionOfTheSameSaleIsIdempotent() {
        String admin = adminToken();
        String barcode = code();
        long id = productWithBarcode(admin, barcode, 10).get("id").asLong();
        String clientSaleId = "retry-" + shortId() + shortId();

        Map<String, Object> body = Map.of(
                "clientSaleId", clientSaleId,
                "items", List.of(Map.of("barcode", barcode, "quantity", 2)));

        ApiClient.Response first = api.post("/api/v1/sales", body, admin);
        ApiClient.Response second = api.post("/api/v1/sales", body, admin);
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.json().get("id").asLong()).isEqualTo(first.json().get("id").asLong());
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(8);
    }

    @Test
    void concurrentDuplicateSubmissionsDeductStockOnce() throws Exception {
        String admin = adminToken();
        String barcode = code();
        long id = productWithBarcode(admin, barcode, 10).get("id").asLong();
        String clientSaleId = "race-" + shortId() + shortId();

        Map<String, Object> body = Map.of(
                "clientSaleId", clientSaleId,
                "items", List.of(Map.of("barcode", barcode, "quantity", 4)));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ApiClient.Response>> calls = java.util.stream.IntStream.range(0, 4)
                    .<Callable<ApiClient.Response>>mapToObj(i -> () -> api.post("/api/v1/sales", body, admin))
                    .toList();
            int created = 0;
            for (Future<ApiClient.Response> future : pool.invokeAll(calls)) {
                int status = future.get().status();
                assertThat(status).as("duplicates must succeed idempotently or conflict").isIn(201, 409);
                if (status == 201) {
                    created++;
                }
            }
            assertThat(created).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE client_sale_id = ?", Integer.class,
                clientSaleId)).isEqualTo(1);
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt())
                .as("stock deducted exactly once").isEqualTo(6);
    }

    @Test
    void checkoutIsAtomicAndSettlesTheServerCalculatedBalance() {
        String admin = adminToken();
        String barcode = code();
        long id = productWithBarcode(admin, barcode, 5).get("id").asLong();
        long cash = paymentMethodId("CASH", admin);
        long mtn = paymentMethodId("MTN", admin);

        // No amount sent: the payment settles the server-calculated total (2 x 11.60).
        ApiClient.Response paid = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("barcode", barcode, "quantity", 2)),
                "payments", List.of(Map.of("paymentMethodId", cash))), admin);
        assertThat(paid.status()).as(paid.body()).isEqualTo(201);
        assertThat(paid.json().get("paymentStatus").asString()).isEqualTo("PAID");
        assertThat(dec(paid.json().get("amountPaid"))).isEqualByComparingTo("23.20");
        assertThat(dec(paid.json().get("balanceDue"))).isEqualByComparingTo("0.00");
        assertThat(paid.json().get("receiptNumber").isNull()).isFalse();

        // Cash tendered above the total still produces change.
        ApiClient.Response change = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("barcode", barcode, "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "20.00"))), admin);
        assertThat(dec(change.json().get("changeGiven"))).isEqualByComparingTo("8.40");
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(2);

        // A failing payment (mobile money without its confirmation code) rolls the whole sale back,
        // including the stock deduction.
        ApiClient.Response failed = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("barcode", barcode, "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", mtn))), admin);
        assertThat(failed.status()).isEqualTo(400);
        assertThat(failed.json().get("message").asString()).contains("transaction reference");
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(2);

        // Mobile money with a code works, and the same code cannot be reused.
        String reference = "MM" + shortId();
        Map<String, Object> momo = Map.of(
                "items", List.of(Map.of("barcode", barcode, "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", mtn, "transactionReference", reference)));
        assertThat(api.post("/api/v1/sales", momo, admin).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/sales", momo, admin).status()).isEqualTo(409);
        assertThat(api.get("/api/v1/products/" + id, admin).json().get("stockQuantity").asInt()).isEqualTo(1);
    }

    @Test
    void permissionsAreEnforced() {
        String admin = adminToken();
        String cashier = userToken("CASHIER");
        String barcode = code();
        JsonNode product = productWithBarcode(admin, barcode, 5);

        // Cashiers can look products up and sell them...
        assertThat(api.get("/api/v1/products/by-barcode?barcode=" + barcode, cashier).status()).isEqualTo(200);
        ApiClient.Response sale = sell(cashier, Map.of("barcode", barcode, "quantity", 1));
        assertThat(sale.status()).as(sale.body()).isEqualTo(201);

        // ...but cannot register or re-barcode products.
        assertThat(createWithBarcode(cashier, "Nope " + shortId(), code(), 1).status()).isEqualTo(403);
        assertThat(api.put("/api/v1/products/" + product.get("id").asLong(), Map.of("barcode", code()), cashier)
                .status()).isEqualTo(403);

        // Managers can.
        String manager = userToken("MANAGER");
        assertThat(createWithBarcode(manager, "Managed " + shortId(), code(), 1).status()).isEqualTo(201);

        // No token -> rejected.
        assertThat(api.get("/api/v1/products/by-barcode?barcode=" + barcode, null).status()).isIn(401, 403);
    }

    @Test
    void migrationNormalisesLegacyBarcodesWithoutLosingData() throws Exception {
        String admin = adminToken();
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);

        long messy = insertLegacyProduct("legacy-m-" + suffix, "  abc" + suffix + " ");
        long blank = insertLegacyProduct("legacy-b-" + suffix, "   ");
        long clean = insertLegacyProduct("legacy-c-" + suffix, "KEEP" + suffix);
        // Two legacy products whose codes collide once upper-cased: neither may be changed or lost.
        long dupA = insertLegacyProduct("legacy-d1-" + suffix, "dup" + suffix);
        long dupB = insertLegacyProduct("legacy-d2-" + suffix, "DUP" + suffix);
        long invalid = insertLegacyProduct("legacy-i-" + suffix, "has space " + suffix);

        jdbc.update("DELETE FROM schema_migrations WHERE version = ?", "2026.10.06-normalise-product-barcodes");
        migrationRunner.run(null);

        assertThat(barcodeOf(messy)).isEqualTo("ABC" + suffix);
        assertThat(barcodeOf(blank)).isNull();
        assertThat(barcodeOf(clean)).isEqualTo("KEEP" + suffix);
        assertThat(barcodeOf(dupA)).isEqualTo("dup" + suffix);
        assertThat(barcodeOf(dupB)).isEqualTo("DUP" + suffix);
        assertThat(barcodeOf(invalid)).isEqualTo("has space " + suffix);

        // Normalised legacy products are now found by scanning.
        assertThat(api.get("/api/v1/products/by-barcode?barcode=abc" + suffix, admin).status()).isEqualTo(200);
        // All rows still exist.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id IN (?,?,?,?,?,?)", Integer.class,
                messy, blank, clean, dupA, dupB, invalid)).isEqualTo(6);
    }

    private long insertLegacyProduct(String name, String barcode) {
        return jdbc.queryForObject("""
                INSERT INTO products (name, barcode, price, stock_quantity, active, track_inventory, vat_rate,
                                      zra_registered, created_at, updated_at)
                VALUES (?, ?, 5.00, 1, TRUE, TRUE, 16.00, FALSE, now(), now()) RETURNING id""",
                Long.class, name, barcode);
    }

    private String barcodeOf(long id) {
        return jdbc.queryForObject("SELECT barcode FROM products WHERE id = ?", String.class, id);
    }
}
