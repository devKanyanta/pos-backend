package com.devkanyanta.pos.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Test double for the ZRA VSDC API: answers the endpoints the backend calls with VSDC-shaped
 * envelopes and records every request so tests can assert on the payloads.
 */
public final class FakeVsdcServer {

    public record Request(String path, String body) {
    }

    private static final FakeVsdcServer INSTANCE = start();

    private final HttpServer server;

    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private final AtomicInteger receiptCounter = new AtomicInteger(1000);

    /** Number of upcoming saveSales calls that should fail with HTTP 503. */
    private final AtomicInteger failNextSales = new AtomicInteger();

    /** Number of upcoming saveSales calls that should be rejected with a VSDC error code. */
    private final AtomicInteger rejectNextSales = new AtomicInteger();

    private FakeVsdcServer(HttpServer server) {
        this.server = server;
    }

    public static FakeVsdcServer get() {
        return INSTANCE;
    }

    private static FakeVsdcServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeVsdcServer fake = new FakeVsdcServer(server);
            server.createContext("/", fake::handle);
            server.start();
            return fake;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/vsdc";
    }

    public List<Request> requests() {
        return requests;
    }

    public List<Request> requests(String pathSuffix) {
        return requests.stream().filter(r -> r.path().endsWith(pathSuffix)).toList();
    }

    public void failNextSales(int count) {
        failNextSales.set(count);
    }

    public void rejectNextSales(int count) {
        rejectNextSales.set(count);
    }

    private void handle(HttpExchange exchange) throws IOException {

        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(path, body));

        String response;
        int status = 200;

        if (path.endsWith("/initializer/selectInitInfo")) {
            response = envelope("000", "It is succeeded", """
                    {"info":{"tpin":"1002345678","taxprNm":"TEST TRADERS LTD","bhfId":"000","bhfNm":"Head Office",
                    "sdcId":"SDC0100001234","mrcNo":"WIS01000123","lastSaleInvcNo":"0"}}""");
        } else if (path.endsWith("/items/saveItem")) {
            response = envelope("000", "It is succeeded", "null");
        } else if (path.endsWith("/code/selectCodes") || path.endsWith("/itemClass/selectItemsClass")) {
            response = envelope("000", "It is succeeded", """
                    {"clsList":[{"cdCls":"10","cdClsNm":"Quantity Unit","dtlList":[{"cd":"U","cdNm":"Pieces"}]}]}""");
        } else if (path.endsWith("/trnsSales/saveSales")) {
            if (failNextSales.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                status = 503;
                response = "{\"error\":\"unavailable\"}";
            } else if (rejectNextSales.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                response = envelope("910", "Request parameter error", "null");
            } else {
                int receiptNo = receiptCounter.incrementAndGet();
                response = envelope("000", "It is succeeded", """
                        {"rcptNo":%d,"intrlData":"INTRLDATA%dXYZ","rcptSign":"SIGN%dABCD",
                        "vsdcRcptPbctDate":"20261023120000","sdcId":"SDC0100001234","mrcNo":"WIS01000123",
                        "qrCodeUrl":"https://sandbox.zra.org.zm/verify?rcpt=%d"}""".formatted(receiptNo, receiptNo, receiptNo, receiptNo));
            }
        } else {
            status = 404;
            response = "{}";
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String envelope(String code, String message, String data) {
        return "{\"resultCd\":\"" + code + "\",\"resultMsg\":\"" + message + "\",\"resultDt\":\"20261023120000\",\"data\":" + data + "}";
    }
}
