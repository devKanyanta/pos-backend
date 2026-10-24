package com.devkanyanta.pos.zra.vsdc;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.devkanyanta.pos.shared.config.PosProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP client for the ZRA Smart Invoice VSDC API. All endpoints are JSON POSTs that return the
 * standard envelope ({@code resultCd}, {@code resultMsg}, {@code resultDt}, {@code data}).
 */
@Component
@Slf4j
public class ZraVsdcClient {

    public static final String PATH_INIT = "/initializer/selectInitInfo";
    public static final String PATH_CODES = "/code/selectCodes";
    public static final String PATH_ITEM_CLASSES = "/itemClass/selectItemsClass";
    public static final String PATH_SAVE_ITEM = "/items/saveItem";
    public static final String PATH_SAVE_SALES = "/trnsSales/saveSales";

    private final PosProperties properties;

    private final ObjectMapper objectMapper;

    private volatile RestClient restClient;

    private volatile String restClientBaseUrl;

    public ZraVsdcClient(PosProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ZraVsdcResponse post(String path, Map<String, Object> payload) {
        return post(path, toJson(payload));
    }

    public ZraVsdcResponse post(String path, String jsonBody) {

        String body;
        try {
            body = client().post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new ZraVsdcException("HTTP_" + ex.getStatusCode().value(),
                    "VSDC returned HTTP " + ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        } catch (ResourceAccessException ex) {
            throw new ZraVsdcException("NETWORK", "VSDC is not reachable: " + ex.getMessage(), null, ex);
        }

        if (body == null || body.isBlank()) {
            throw new ZraVsdcException("EMPTY_RESPONSE", "VSDC returned an empty response", body, null);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException ex) {
            throw new ZraVsdcException("INVALID_RESPONSE", "VSDC returned a non-JSON response", body, ex);
        }

        ZraVsdcResponse response = new ZraVsdcResponse(
                text(root, "resultCd"),
                text(root, "resultMsg"),
                text(root, "resultDt"),
                root.get("data"),
                body
        );

        if (!response.isSuccess()) {
            throw new ZraVsdcException(response.resultCd() != null ? response.resultCd() : "UNKNOWN",
                    "VSDC rejected the request: " + response.resultMsg(), body, null);
        }

        log.debug("VSDC {} -> {} {}", path, response.resultCd(), response.resultMsg());
        return response;
    }

    public String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private RestClient client() {

        String baseUrl = properties.getZra().getBaseUrl();
        RestClient current = restClient;

        if (current == null || !baseUrl.equals(restClientBaseUrl)) {
            synchronized (this) {
                if (restClient == null || !baseUrl.equals(restClientBaseUrl)) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(Duration.ofSeconds(properties.getZra().getConnectTimeoutSeconds()));
                    factory.setReadTimeout(Duration.ofSeconds(properties.getZra().getReadTimeoutSeconds()));
                    restClient = RestClient.builder()
                            .baseUrl(baseUrl)
                            .requestFactory(factory)
                            .build();
                    restClientBaseUrl = baseUrl;
                }
                current = restClient;
            }
        }

        return current;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
