package com.devkanyanta.pos.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.devkanyanta.pos.user.UserRepository;
import com.devkanyanta.pos.user.role.RoleRepository;

import tools.jackson.databind.JsonNode;

/**
 * Boots the full application on a random port against the PostgreSQL test database and the fake
 * VSDC, and provides helpers for authenticated API calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String ADMIN = "owner";
    protected static final String ADMIN_PASSWORD = "owner-pass-1";

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    protected ApiClient api;

    protected FakeVsdcServer vsdc = FakeVsdcServer.get();

    @DynamicPropertySource
    static void vsdcProperties(DynamicPropertyRegistry registry) {
        registry.add("pos.zra.base-url", () -> FakeVsdcServer.get().baseUrl());
    }

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(port);
    }

    /** Logs in as the shop owner, registering it first if this is a fresh database. */
    protected synchronized String adminToken() {

        if (!userRepository.existsByUsername(ADMIN)) {
            assertThat(userRepository.count())
                    .as("the owner must be the first account so it becomes ADMIN")
                    .isZero();
            ApiClient.Response response = api.post("/api/v1/auth/register", Map.of(
                    "firstName", "Shop", "lastName", "Owner", "email", "owner@example.com",
                    "phoneNumber", "0977000000", "username", ADMIN, "password", ADMIN_PASSWORD), null);
            assertThat(response.status()).as(response.body()).isEqualTo(200);
            assertThat(response.json().get("roles").toString()).contains("ADMIN");
        }

        return login(ADMIN, ADMIN_PASSWORD);
    }

    protected String login(String username, String password) {
        ApiClient.Response response = api.post("/api/v1/auth/login",
                Map.of("username", username, "password", password), null);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json().get("token").asString();
    }

    /** Creates a user with the given role through the admin API and returns its token. */
    protected String userToken(String role) {

        String admin = adminToken();
        String username = role.toLowerCase() + "-" + shortId();
        long roleId = roleRepository.findByName(role).orElseThrow().getId();

        ApiClient.Response created = api.post("/api/v1/users", Map.of(
                "firstName", "Test", "lastName", role, "email", username + "@example.com",
                "username", username, "password", "secret-123", "roleIds", List.of(roleId)), admin);
        assertThat(created.status()).as(created.body()).isEqualTo(201);

        return login(username, "secret-123");
    }

    protected static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected long paymentMethodId(String code, String token) {
        JsonNode methods = api.get("/api/v1/payment-methods", token).json();
        for (JsonNode method : methods) {
            if (code.equals(method.get("code").asString())) {
                return method.get("id").asLong();
            }
        }
        throw new IllegalStateException("Payment method not found: " + code);
    }

    /** Creates a ZRA-mapped product and returns its JSON. */
    protected JsonNode createProduct(String token, String name, String price, int stock, String taxType) {

        ApiClient.Response response = api.post("/api/v1/products", Map.of(
                "name", name,
                "sku", "SKU-" + shortId(),
                "price", price,
                "costPrice", "1.00",
                "stockQuantity", stock,
                "zraTaxType", taxType,
                "zraItemClassCode", "50202300",
                "zraItemTypeCode", "2",
                "zraQuantityUnitCode", "U",
                "zraPackagingUnitCode", "NT"), token);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.json();
    }

    protected static java.math.BigDecimal dec(JsonNode node) {
        return node.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
    }

    protected static java.math.BigDecimal dec(String value) {
        return new java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
