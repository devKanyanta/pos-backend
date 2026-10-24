package com.devkanyanta.pos.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Minimal HTTP client for exercising the running application end to end. */
public class ApiClient {

    public static final ObjectMapper JSON = JsonMapper.builder().build();

    public record Response(int status, String body, String contentType, byte[] bytes) {

        public JsonNode json() {
            return JSON.readTree(body);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    private final String baseUrl;

    public ApiClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public Response get(String path, String token) {
        return send(builder(path, token).GET().build());
    }

    public Response delete(String path, String token) {
        return send(builder(path, token).DELETE().build());
    }

    public Response post(String path, Object body, String token) {
        return send(builder(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : toJson(body)))
                .build());
    }

    public Response put(String path, Object body, String token) {
        return send(builder(path, token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build());
    }

    public Response postRaw(String path, String contentType, byte[] body, String token) {
        return send(builder(path, token)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
    }

    /** multipart/form-data upload with a single file part. */
    public Response upload(String path, String field, String filename, String content, String token) {

        String boundary = "----PosTestBoundary" + System.nanoTime();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + content + "\r\n"
                + "--" + boundary + "--\r\n";

        return postRaw(path, "multipart/form-data; boundary=" + boundary, body.getBytes(StandardCharsets.UTF_8), token);
    }

    private static String toJson(Object body) {
        return body instanceof String s ? s : JSON.writeValueAsString(body);
    }

    private HttpRequest.Builder builder(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(),
                    new String(response.body(), StandardCharsets.UTF_8),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body());
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
