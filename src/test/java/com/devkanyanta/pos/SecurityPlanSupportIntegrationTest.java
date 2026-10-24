package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/** Authorisation, error mapping, merchant profile / plan gating and WhatsApp support. */
class SecurityPlanSupportIntegrationTest extends IntegrationTestBase {

    @Test
    void endpointsEnforceAuthenticationPermissionsAndReturnTypedErrors() {

        String admin = adminToken();
        String cashier = userToken("CASHIER");

        assertThat(api.get("/api/v1/sales", null).status()).isIn(401, 403);
        assertThat(api.get("/api/v1/users", cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/users", Map.of("username", "x"), cashier).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/reports/sales/summary?from=2026-01-01&to=2026-01-31", cashier).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/zra/device/initialize", Map.of(), cashier).status()).isEqualTo(403);
        assertThat(api.put("/api/v1/merchant", Map.of("plan", "MICRO"), cashier).status()).isEqualTo(403);

        // Typed errors instead of HTTP 500.
        assertThat(api.get("/api/v1/sales/99999999", admin).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/sales/not-a-number", admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/sales", "{not json", admin).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/sales", Map.of("items", List.of()), admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/reports/sales/daily", admin).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/no/such/endpoint", admin).status()).isEqualTo(404);

        ApiClient.Response duplicateUser = api.post("/api/v1/auth/register", Map.of(
                "username", ADMIN, "email", "other@example.com", "password", "whatever1"), null);
        assertThat(duplicateUser.status()).isEqualTo(409);

        // Later self-registrations are cashiers, never admins.
        ApiClient.Response later = api.post("/api/v1/auth/register", Map.of(
                "username", "self-" + shortId(), "email", shortId() + "@example.com", "password", "whatever1"), null);
        assertThat(later.status()).isEqualTo(200);
        assertThat(later.json().get("roles").toString()).contains("CASHIER").doesNotContain("ADMIN");

        assertThat(api.post("/api/v1/auth/login", Map.of("username", ADMIN, "password", "wrong"), null).status())
                .isEqualTo(401);
    }

    @Test
    void microPlanLimitsFeaturesAndProfileDrivesReceiptsAndSupport() {

        String admin = adminToken();

        ApiClient.Response profile = api.put("/api/v1/merchant", Map.of(
                "businessName", "Chilenje Groceries", "tpin", "1002345678", "phone", "0977000001",
                "address", "Plot 12, Chilenje, Lusaka", "receiptFooter", "No refunds without receipt"), admin);
        assertThat(profile.status()).as(profile.body()).isEqualTo(200);
        String accountId = profile.json().get("accountId").asString();
        assertThat(accountId).startsWith("POS-");
        assertThat(api.put("/api/v1/merchant", Map.of("tpin", "12"), admin).status()).isEqualTo(400);

        // WhatsApp Help button: pre-filled with the merchant account id.
        String cashier = userToken("CASHIER");
        JsonNode help = api.get("/api/v1/support/whatsapp?topic=Printer%20not%20working", cashier).json();
        assertThat(help.get("supportNumber").asString()).isEqualTo("260971000111");
        assertThat(help.get("message").asString()).contains("Account ID: " + accountId).contains("Printer not working");
        assertThat(help.get("url").asString()).startsWith("https://wa.me/260971000111?text=").contains(accountId);

        // Support guides.
        ApiClient.Response guide = api.post("/api/v1/support/guides", Map.of(
                "title", "How to issue a credit note", "topic", "credit notes",
                "description", "Open the sale, tap Refund, choose the items.",
                "mediaUrl", "https://support.example.com/videos/credit-note"), admin);
        assertThat(guide.status()).as(guide.body()).isEqualTo(201);
        assertThat(guide.json().get("topic").asString()).isEqualTo("CREDIT_NOTES");
        long guideId = guide.json().get("id").asLong();
        assertThat(api.post("/api/v1/support/guides", Map.of("title", "x", "topic", "y", "mediaUrl", "ftp://bad"), admin)
                .status()).isEqualTo(400);
        assertThat(api.post("/api/v1/support/guides", Map.of("title", "x", "topic", "y"), cashier).status()).isEqualTo(403);

        JsonNode guides = api.get("/api/v1/support/guides?topic=credit_notes", cashier).json();
        assertThat(guides.toString()).contains("How to issue a credit note");
        assertThat(guides.get(0).get("requestViaWhatsAppUrl").asString()).contains("260971000111");

        JsonNode shared = api.post("/api/v1/support/guides/" + guideId + "/share", Map.of("phone", "+260 96 555 1234"), cashier).json();
        assertThat(shared.get("shareUrl").asString()).startsWith("https://wa.me/260965551234?text=");
        assertThat(shared.get("text").asString()).contains("*How to issue a credit note*");

        // Receipt share via WhatsApp (click-to-chat; provider sending is not configured).
        JsonNode product = createProduct(admin, "Candles " + shortId(), "5.00", 10, "A");
        long cash = paymentMethodId("CASH", admin);
        JsonNode sale = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "10.00"))), cashier).json();
        long receiptId = sale.get("receiptId").asLong();

        ApiClient.Response receiptShare = api.post("/api/v1/receipts/" + receiptId + "/share/whatsapp",
                Map.of("phone", "0965551234"), cashier);
        assertThat(receiptShare.status()).as(receiptShare.body()).isEqualTo(200);
        assertThat(receiptShare.json().get("text").asString()).contains("CHILENJE GROCERIES")
                .contains("TPIN: 1002345678").contains("No refunds without receipt");
        assertThat(receiptShare.json().get("shareUrl").asString()).startsWith("https://wa.me/260965551234?text=");
        assertThat(api.post("/api/v1/receipts/" + receiptId + "/share/whatsapp?send=true",
                Map.of("phone", "0965551234"), cashier).status()).isEqualTo(503);
        assertThat(api.post("/api/v1/receipts/" + receiptId + "/share/whatsapp",
                Map.of("phone", "12"), cashier).status()).isEqualTo(400);

        // MICRO plan: no ZRA, no reports, no extra users; selling and today's total still work.
        assertThat(api.put("/api/v1/merchant", Map.of("plan", "MICRO"), admin).json().get("features").get("zra").asBoolean())
                .isFalse();
        try {
            assertThat(api.get("/api/v1/reports/sales/summary?from=2026-01-01&to=2026-01-31", admin).status()).isEqualTo(403);
            assertThat(api.get("/api/v1/reports/z-report", admin).status()).isEqualTo(403);
            assertThat(api.post("/api/v1/zra/device/initialize", Map.of("tpin", "1002345678",
                    "deviceSerialNo", "X"), admin).status()).isEqualTo(403);
            assertThat(api.post("/api/v1/users", Map.of("username", "u" + shortId(), "email", shortId() + "@x.com",
                    "password", "secret-123"), admin).status()).isEqualTo(403);
            ApiClient.Response microSale = api.post("/api/v1/sales", Map.of(
                    "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 1)),
                    "payments", List.of(Map.of("paymentMethodId", cash, "amount", "5.80"))), admin);
            assertThat(microSale.status()).isEqualTo(201);
            assertThat(microSale.json().get("zraStatus").isNull()).isTrue(); // not queued for ZRA
            assertThat(api.get("/api/v1/reports/today", admin).status()).isEqualTo(200);
        } finally {
            assertThat(api.put("/api/v1/merchant", Map.of("plan", "SME"), admin).status()).isEqualTo(200);
        }
    }
}
