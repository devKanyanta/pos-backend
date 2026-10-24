package com.devkanyanta.pos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.devkanyanta.pos.support.ApiClient;
import com.devkanyanta.pos.support.IntegrationTestBase;
import com.devkanyanta.pos.zra.ZraSubmissionWorker;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end: catalogue with ZRA mapping, multi-tender sale with change and mobile money
 * reference, receipt, ZRA device initialisation and invoice signing (against the fake VSDC),
 * partial refund with ZRA credit note, retries.
 */
class SalesZraFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    ZraSubmissionWorker worker;

    @Test
    void saleIsTotalledPaidReceiptedAndSignedByZra() {

        String token = adminToken();
        initialiseVsdc(token);

        JsonNode coke = createProduct(token, "Coke 500ml " + shortId(), "15.00", 50, "A");
        JsonNode bread = createProduct(token, "Bread " + shortId(), "10.00", 20, "C3");
        assertThat(coke.get("zraMapped").asBoolean()).isTrue();
        assertThat(dec(bread.get("vatRate"))).isEqualByComparingTo("0.00");

        long cash = paymentMethodId("CASH", token);
        long mtn = paymentMethodId("MTN", token);
        String momoRef = "MP" + shortId().toUpperCase();
        String clientSaleId = "web-" + shortId() + shortId();

        // 2 x 15.00 (line discount 2.00) + 1 x 10.00, sale discount 3.80
        // line totals 28.00 + 10.00 = 38.00; discount split 2.80 / 1.00
        // taxable 25.20 (16% -> 4.03) + 9.00 (0%) => total 38.23
        Map<String, Object> sale = Map.of(
                "clientSaleId", clientSaleId,
                "discountAmount", "3.80",
                "items", List.of(
                        Map.of("productId", coke.get("id").asLong(), "quantity", 2, "discountAmount", "2.00"),
                        Map.of("productId", bread.get("id").asLong(), "quantity", 1)),
                "payments", List.of(
                        Map.of("paymentMethodId", mtn, "amount", "20.00", "transactionReference", momoRef,
                                "payerPhone", "0961234567"),
                        Map.of("paymentMethodId", cash, "amount", "50.00")));

        ApiClient.Response created = api.post("/api/v1/sales", sale, token);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        JsonNode body = created.json();

        assertThat(dec(body.get("subtotal"))).isEqualByComparingTo("38.00");
        assertThat(dec(body.get("discountAmount"))).isEqualByComparingTo("3.80");
        assertThat(dec(body.get("vatAmount"))).isEqualByComparingTo("4.03");
        assertThat(dec(body.get("totalAmount"))).isEqualByComparingTo("38.23");
        assertThat(body.get("paymentStatus").asString()).isEqualTo("PAID");
        assertThat(dec(body.get("changeGiven"))).isEqualByComparingTo("31.77");
        assertThat(dec(body.get("balanceDue"))).isEqualByComparingTo("0.00");
        assertThat(body.get("receiptNumber").asString()).startsWith("RCT-");
        assertThat(body.get("zraStatus").asString()).isEqualTo("PENDING");

        long saleId = body.get("id").asLong();
        long receiptId = body.get("receiptId").asLong();

        // Stock was deducted with SALE movements.
        assertThat(api.get("/api/v1/products/" + coke.get("id").asLong(), token).json().get("stockQuantity").asInt())
                .isEqualTo(48);
        JsonNode movements = api.get("/api/v1/inventory-movements/product/" + coke.get("id").asLong(), token).json();
        assertThat(movements.toString()).contains("\"movementType\":\"SALE\"").contains("\"quantity\":-2");

        // Same clientSaleId again: idempotent, no second sale or stock deduction.
        ApiClient.Response replay = api.post("/api/v1/sales", sale, token);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.json().get("id").asLong()).isEqualTo(saleId);
        assertThat(api.get("/api/v1/products/" + coke.get("id").asLong(), token).json().get("stockQuantity").asInt())
                .isEqualTo(48);

        // The same mobile money confirmation code cannot be used twice.
        ApiClient.Response second = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", bread.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", mtn, "amount", "10.00",
                        "transactionReference", momoRef.toLowerCase()))), token);
        assertThat(second.status()).as(second.body()).isEqualTo(409);

        // Mobile money without a reference is rejected.
        ApiClient.Response noRef = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", bread.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", mtn, "amount", "10.00"))), token);
        assertThat(noRef.status()).isEqualTo(400);
        assertThat(noRef.json().get("message").asString()).contains("transaction reference");

        // Transmit to the (fake) VSDC: items are registered first, then the invoice is signed.
        worker.process(submissionForSale(saleId, "SALE_INVOICE", token).get("id").asLong());

        JsonNode receipt = api.get("/api/v1/receipts/" + receiptId, token).json();
        assertThat(receipt.get("zraStatus").asString()).isEqualTo("SUCCESS");
        assertThat(receipt.get("zraVerified").asBoolean()).isTrue();
        assertThat(receipt.get("zraReceiptSignature").asString()).startsWith("SIGN");
        assertThat(receipt.get("zraSdcId").asString()).isEqualTo("SDC0100001234");

        JsonNode invoice = salesRequest(body.get("receiptNumber").asString());
        assertThat(invoice.get("rcptTyCd").asString()).isEqualTo("S");
        // MTN covered 20.00 of 38.23 (cash applied 18.23), so the dominant tender is mobile money.
        assertThat(invoice.get("pmtTyCd").asString()).isEqualTo("06");
        assertThat(invoice.get("cisInvcNo").asString()).isEqualTo(body.get("receiptNumber").asString());
        assertThat(dec(invoice.get("totAmt"))).isEqualByComparingTo("38.23");
        assertThat(dec(invoice.get("taxblAmtA"))).isEqualByComparingTo("25.20");
        assertThat(dec(invoice.get("taxAmtA"))).isEqualByComparingTo("4.03");
        assertThat(dec(invoice.get("taxblAmtC3"))).isEqualByComparingTo("9.00");
        assertThat(invoice.get("itemList").size()).isEqualTo(2);
        assertThat(invoice.get("itemList").get(0).get("vatCatCd").asString()).isEqualTo("A");
        assertThat(vsdc.requests("/items/saveItem")).isNotEmpty();
        assertThat(api.get("/api/v1/products/" + coke.get("id").asLong(), token).json().get("zraRegistered").asBoolean())
                .isTrue();

        // Printable receipt carries the ZRA verification block.
        ApiClient.Response text = api.get("/api/v1/receipts/" + receiptId + "/print?width=40", token);
        assertThat(text.status()).isEqualTo(200);
        assertThat(text.body()).contains("ZRA SMART INVOICE").contains("Verification: SIGN")
                .contains("TOTAL ZMW").contains("Change").contains("Ref: " + momoRef);

        ApiClient.Response pdf = api.get("/api/v1/receipts/" + receiptId + "/pdf", token);
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(new String(pdf.bytes(), 0, 5)).isEqualTo("%PDF-");

        // Partial refund: 1 of the 2 Cokes, paid out in cash.
        long cokeItemId = body.get("items").get(0).get("id").asLong();
        ApiClient.Response refund = api.post("/api/v1/sales/" + saleId + "/refund", Map.of(
                "items", List.of(Map.of("saleItemId", cokeItemId, "quantity", 1)),
                "reason", "Damaged bottle",
                "zraReasonCode", "03",
                "refundPaymentMethodId", cash), token);
        assertThat(refund.status()).as(refund.body()).isEqualTo(200);
        JsonNode refunded = refund.json();
        assertThat(refunded.get("saleStatus").asString()).isEqualTo("ACTIVE");
        // Coke line incl. VAT = 25.20 + 4.03 = 29.23; half of it = 14.62 (rounded)
        assertThat(dec(refunded.get("refundedAmount"))).isEqualByComparingTo("14.62");
        assertThat(refunded.get("refunds").get(0).get("refundNumber").asString()).startsWith("CRN-");
        assertThat(refunded.get("items").get(0).get("refundedQuantity").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/products/" + coke.get("id").asLong(), token).json().get("stockQuantity").asInt())
                .isEqualTo(49);

        // Cannot refund more than remains.
        ApiClient.Response tooMuch = api.post("/api/v1/sales/" + saleId + "/refund", Map.of(
                "items", List.of(Map.of("saleItemId", cokeItemId, "quantity", 2))), token);
        assertThat(tooMuch.status()).isEqualTo(400);

        // Credit note goes to ZRA referencing the original invoice.
        worker.process(submissionForSale(saleId, "CREDIT_NOTE", token).get("id").asLong());
        JsonNode creditNote = salesRequest(refunded.get("refunds").get(0).get("refundNumber").asString());
        assertThat(creditNote.get("rcptTyCd").asString()).isEqualTo("R");
        assertThat(creditNote.get("rfdRsnCd").asString()).isEqualTo("03");
        assertThat(creditNote.get("orgInvcNo").asLong()).isEqualTo(invoice.get("invcNo").asLong());
        assertThat(dec(creditNote.get("totAmt"))).isEqualByComparingTo("14.62");

        JsonNode afterRefund = api.get("/api/v1/sales/" + saleId, token).json();
        assertThat(afterRefund.get("refunds").get(0).get("zraStatus").asString()).isEqualTo("SUCCESS");
        assertThat(afterRefund.get("refunds").get(0).get("zraReceiptNumber").isNull()).isFalse();

        // Refund the rest: sale becomes REFUNDED.
        ApiClient.Response rest = api.post("/api/v1/sales/" + saleId + "/refund", Map.of("reason", "Customer returned all"), token);
        assertThat(rest.status()).as(rest.body()).isEqualTo(200);
        assertThat(rest.json().get("saleStatus").asString()).isEqualTo("REFUNDED");
        assertThat(rest.json().get("paymentStatus").asString()).isEqualTo("REFUNDED");
        assertThat(dec(rest.json().get("refundedAmount"))).isEqualByComparingTo("38.23");
    }

    @Test
    void failedTransmissionsAreRetriedThenMarkedFailedAndCanBeRetriedManually() {

        String token = adminToken();
        initialiseVsdc(token);

        JsonNode product = createProduct(token, "Sugar 1kg " + shortId(), "30.00", 10, "A");
        long cash = paymentMethodId("CASH", token);

        ApiClient.Response sale = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodId", cash, "amount", "34.80"))), token);
        assertThat(sale.status()).as(sale.body()).isEqualTo(201);
        long saleId = sale.json().get("id").asLong();

        long submissionId = submissionForSale(saleId, "SALE_INVOICE", token).get("id").asLong();

        // VSDC down: the sale is unaffected and the submission is scheduled for retry.
        vsdc.failNextSales(1);
        worker.process(submissionId);
        JsonNode submission = submissionForSale(saleId, "SALE_INVOICE", token);
        assertThat(submission.get("submissionStatus").asString()).isEqualTo("RETRYING");
        assertThat(submission.get("errorCode").asString()).isEqualTo("HTTP_503");
        assertThat(submission.get("retryCount").asInt()).isEqualTo(1);

        // VSDC rejects twice more -> max attempts (3) reached -> FAILED.
        vsdc.rejectNextSales(2);
        worker.process(submissionId);
        worker.process(submissionId);
        submission = submissionForSale(saleId, "SALE_INVOICE", token);
        assertThat(submission.get("submissionStatus").asString()).isEqualTo("FAILED");
        assertThat(submission.get("errorCode").asString()).isEqualTo("910");

        // The same invoice number is kept across attempts.
        List<JsonNode> attempts = vsdc.requests("/trnsSales/saveSales").stream()
                .map(r -> ApiClient.JSON.readTree(r.body()))
                .filter(json -> json.get("cisInvcNo").asString().equals(sale.json().get("receiptNumber").asString()))
                .toList();
        assertThat(attempts).hasSize(3);
        assertThat(attempts.stream().map(a -> a.get("invcNo").asLong()).distinct()).hasSize(1);

        // Manual retry succeeds.
        ApiClient.Response retried = api.post("/api/v1/zra-submissions/" + submission.get("id").asLong() + "/retry", null, token);
        assertThat(retried.status()).as(retried.body()).isEqualTo(200);
        assertThat(retried.json().get("submissionStatus").asString()).isEqualTo("SUCCESS");
        assertThat(retried.json().get("requestPayload").asString()).contains("\"rcptTyCd\":\"S\"");
    }

    @Test
    void unpaidSalesCanBeVoidedAndPaidSalesCannot() {

        String token = adminToken();
        JsonNode product = createProduct(token, "Milk " + shortId(), "12.50", 5, "A");

        ApiClient.Response sale = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 2))), token);
        assertThat(sale.status()).isEqualTo(201);
        assertThat(sale.json().get("paymentStatus").asString()).isEqualTo("PENDING");
        long saleId = sale.json().get("id").asLong();

        // Refund of an unpaid sale is refused; void is the way.
        assertThat(api.post("/api/v1/sales/" + saleId + "/refund", Map.of(), token).status()).isEqualTo(400);

        ApiClient.Response voided = api.post("/api/v1/sales/" + saleId + "/void", null, token);
        assertThat(voided.status()).isEqualTo(200);
        assertThat(voided.json().get("saleStatus").asString()).isEqualTo("VOIDED");
        assertThat(api.get("/api/v1/products/" + product.get("id").asLong(), token).json().get("stockQuantity").asInt())
                .isEqualTo(5);

        // Overselling is refused online.
        ApiClient.Response oversell = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 6))), token);
        assertThat(oversell.status()).isEqualTo(400);
        assertThat(oversell.json().get("message").asString()).contains("Insufficient stock");

        // Card payments may not exceed the balance (no change on card).
        long card = paymentMethodId("CARD", token);
        ApiClient.Response pending = api.post("/api/v1/sales", Map.of(
                "items", List.of(Map.of("productId", product.get("id").asLong(), "quantity", 1))), token);
        ApiClient.Response overpay = api.post("/api/v1/sales/" + pending.json().get("id").asLong() + "/payments",
                Map.of("paymentMethodId", card, "amount", "100.00"), token);
        assertThat(overpay.status()).isEqualTo(400);

        // Partial payment then the rest.
        ApiClient.Response part = api.post("/api/v1/sales/" + pending.json().get("id").asLong() + "/payments",
                Map.of("paymentMethodId", card, "amount", "5.00", "transactionReference", "AUTH123"), token);
        assertThat(part.status()).as(part.body()).isEqualTo(201);
        assertThat(part.json().get("salePaymentStatus").asString()).isEqualTo("PARTIALLY_PAID");
        assertThat(dec(part.json().get("saleBalanceDue"))).isEqualByComparingTo("9.50");
    }

    private void initialiseVsdc(String token) {
        ApiClient.Response response = api.post("/api/v1/zra/device/initialize", Map.of(
                "tpin", "1002345678", "bhfId", "000", "deviceSerialNo", "POS-TEST-001"), token);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        assertThat(response.json().get("sdcId").asString()).isEqualTo("SDC0100001234");
    }

    /** The last saveSales request sent for a document number (receipt or credit note number). */
    private JsonNode salesRequest(String documentNumber) {
        List<JsonNode> matches = vsdc.requests("/trnsSales/saveSales").stream()
                .map(r -> ApiClient.JSON.readTree(r.body()))
                .filter(json -> documentNumber.equals(json.get("cisInvcNo").asString()))
                .toList();
        assertThat(matches).as("VSDC requests for " + documentNumber).isNotEmpty();
        return matches.get(matches.size() - 1);
    }

    private JsonNode submissionForSale(long saleId, String type, String token) {
        for (JsonNode submission : api.get("/api/v1/zra-submissions", token).json()) {
            if (!submission.get("saleId").isNull() && submission.get("saleId").asLong() == saleId
                    && type.equals(submission.get("submissionType").asString())) {
                return submission;
            }
        }
        throw new IllegalStateException("No " + type + " submission for sale " + saleId);
    }
}
