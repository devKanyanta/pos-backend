package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/** End-of-day Z-report: expected per tender vs declared, variances, exports and sharing. */
class ReconciliationIntegrationTest extends IntegrationTestBase {

    @Test
    void zReportReconcilesCashAndMobileMoneyAndFlagsVariances() {

        String admin = adminToken();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Lusaka"));

        long cash = paymentMethodId("CASH", admin);
        long mtn = paymentMethodId("MTN", admin);

        JsonNode before = api.get("/api/v1/reports/z-report", admin).json();
        Map<String, BigDecimal> expectedBefore = expectedByCode(before);
        long salesBefore = before.get("sales").get("count").asLong();

        // Exempt products keep the arithmetic simple (no VAT).
        JsonNode maize = createProduct(admin, "Mealie meal " + shortId(), "100.00", 10, "D");
        JsonNode eggs = createProduct(admin, "Eggs tray " + shortId(), "50.00", 10, "D");
        JsonNode oil = createProduct(admin, "Cooking oil " + shortId(), "20.00", 10, "D");

        // Cash sale with change: customer hands over 120 for 100.
        assertThat(api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", maize.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "120.00"))), admin).status()).isEqualTo(201);

        // MTN sale.
        assertThat(api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", eggs.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", mtn, "amount", "50.00",
                        "transactionReference", "MTN" + shortId()))), admin).status()).isEqualTo(201);

        // Cash sale that is refunded in cash the same day.
        ApiClient.Response refundable = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", oil.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "20.00"))), admin);
        assertThat(api.post("/api/v1/sales/" + refundable.json().get("id").asLong() + "/refund",
                Map.of("reason", "Wrong item"), admin).status()).isEqualTo(200);

        JsonNode after = api.get("/api/v1/reports/z-report?date=" + today, admin).json();
        Map<String, BigDecimal> expectedAfter = expectedByCode(after);

        // Cash: +100 applied (change excluded) +20 received -20 refunded = +100.  MTN: +50.
        assertThat(expectedAfter.get("CASH").subtract(expectedBefore.get("CASH"))).isEqualByComparingTo("100.00");
        assertThat(expectedAfter.get("MTN").subtract(expectedBefore.get("MTN"))).isEqualByComparingTo("50.00");
        assertThat(after.get("sales").get("count").asLong() - salesBefore).isEqualTo(3);
        assertThat(after.get("reconciliationStatus").asString()).isEqualTo("NOT_RECONCILED");
        assertThat(tender(after, "CASH").get("declared").isNull()).isTrue();

        // Cash-up with a K200 float and K5 short in the drawer; everything else exact.
        BigDecimal float200 = dec("200.00");
        List<Map<String, Object>> declared = new ArrayList<>();
        for (JsonNode tender : after.get("tenders")) {
            BigDecimal amount = dec(tender.get("expected"));
            if ("CASH".equals(tender.get("code").asString())) {
                amount = amount.add(float200).subtract(dec("5.00"));
            }
            declared.add(Map.of("paymentMethodId", tender.get("paymentMethodId").asLong(), "amount", amount));
        }

        ApiClient.Response submitted = api.post("/api/v1/reconciliations", Map.of(
                "businessDate", today.toString(),
                "openingFloat", "200.00",
                "declared", declared,
                "notes", "Counted by owner"), admin);
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(201);
        assertThat(submitted.json().get("status").asString()).isEqualTo("SHORT");
        assertThat(dec(submitted.json().get("varianceTotal"))).isEqualByComparingTo("-5.00");

        JsonNode reconciled = api.get("/api/v1/reports/z-report?date=" + today, admin).json();
        JsonNode cashLine = tender(reconciled, "CASH");
        assertThat(reconciled.get("reconciliationStatus").asString()).isEqualTo("SHORT");
        assertThat(dec(cashLine.get("variance"))).isEqualByComparingTo("-5.00");
        assertThat(cashLine.get("flagged").asBoolean()).isTrue();
        assertThat(tender(reconciled, "MTN").get("flagged").asBoolean()).isFalse();
        assertThat(dec(reconciled.get("expectedCashInDrawer")))
                .isEqualByComparingTo(expectedAfter.get("CASH").add(float200));
        assertThat(reconciled.get("reconciledBy").asString()).isEqualTo(ADMIN);

        // Exports.
        ApiClient.Response csv = api.get("/api/v1/reports/z-report/csv?date=" + today, admin);
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.contentType()).startsWith("text/csv");
        assertThat(csv.body()).contains("Tender,Code,Transactions,Received").contains("Reconciliation status,SHORT");

        ApiClient.Response pdf = api.get("/api/v1/reports/z-report/pdf?date=" + today, admin);
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(new String(pdf.bytes(), 0, 5)).isEqualTo("%PDF-");

        JsonNode whatsapp = api.get("/api/v1/reports/z-report/whatsapp?date=" + today + "&phone=0977123456", admin).json();
        assertThat(whatsapp.get("text").asString()).contains("*Z-Report " + today + "*").contains("Status: SHORT");
        assertThat(whatsapp.get("shareUrl").asString()).startsWith("https://wa.me/260977123456?text=");
        assertThat(whatsapp.get("canSendDirectly").asBoolean()).isFalse();

        // Sending through a provider is not configured in tests.
        ApiClient.Response send = api.post("/api/v1/reports/z-report/whatsapp/send", Map.of("phone", "0977123456"), admin);
        assertThat(send.status()).isEqualTo(503);

        // Recount: exact amounts -> BALANCED (replaces the earlier count).
        List<Map<String, Object>> exact = new ArrayList<>();
        for (JsonNode tender : reconciled.get("tenders")) {
            exact.add(Map.of("paymentMethodId", tender.get("paymentMethodId").asLong(), "amount", dec(tender.get("expected"))));
        }
        ApiClient.Response recount = api.post("/api/v1/reconciliations", Map.of(
                "openingFloat", "200.00", "declared", exact), admin);
        assertThat(recount.status()).as(recount.body()).isEqualTo(201);
        assertThat(recount.json().get("status").asString()).isEqualTo("BALANCED");

        assertThat(api.get("/api/v1/reconciliations/" + today, admin).json().get("status").asString()).isEqualTo("BALANCED");
        assertThat(api.get("/api/v1/reconciliations", admin).body()).contains(today.toString());

        // Validation.
        assertThat(api.post("/api/v1/reconciliations", Map.of("businessDate", today.plusDays(1).toString()), admin).status())
                .isEqualTo(400);
        assertThat(api.post("/api/v1/reconciliations", Map.of("declared",
                List.of(Map.of("paymentMethodId", cash, "amount", "-1"))), admin).status()).isEqualTo(400);

        // Cashiers cannot see the Z-report.
        String cashier = userToken("CASHIER");
        assertThat(api.get("/api/v1/reports/z-report", cashier).status()).isEqualTo(403);
        // ...but can see today's total.
        JsonNode todayTotal = api.get("/api/v1/reports/today", cashier).json();
        assertThat(todayTotal.get("salesCount").asLong()).isGreaterThanOrEqualTo(3);
    }

    private static Map<String, BigDecimal> expectedByCode(JsonNode report) {
        Map<String, BigDecimal> map = new HashMap<>();
        map.put("CASH", BigDecimal.ZERO);
        map.put("MTN", BigDecimal.ZERO);
        for (JsonNode tender : report.get("tenders")) {
            map.put(tender.get("code").asString(), dec(tender.get("expected")));
        }
        return map;
    }

    private static JsonNode tender(JsonNode report, String code) {
        for (JsonNode tender : report.get("tenders")) {
            if (code.equals(tender.get("code").asString())) {
                return tender;
            }
        }
        throw new IllegalStateException("No tender " + code);
    }
}
