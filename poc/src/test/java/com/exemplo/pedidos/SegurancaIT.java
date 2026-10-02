package com.exemplo.pedidos;

import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.TOKEN_BRUNO;
import static com.exemplo.pedidos.support.Requests.TOKEN_ERP;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.ContractValidator;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cenários API-01, API-02 e API-03 do threat model, com a autenticação simplificada da PoC. */
@IntegrationTest
class SegurancaIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    @DisplayName("Autenticação: sem token ou com token desconhecido, 401 na v2 em Problem Details")
    void unauthorizedV2() {
        for (String token : new String[] {null, "token-inexistente"}) {
            ApiClient.Response post = api.post("/v2/orders", token, newKey(), Requests.v2(newCustomerId()));
            ApiClient.Response get = api.get("/v2/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70", token);

            assertThat(post.status()).isEqualTo(401);
            assertThat(get.status()).isEqualTo(401);
            ContractValidator.V2.assertValid("POST", "/orders", 401, post.headers(), post.body());
            ContractValidator.V2.assertValid("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70", 401,
                    get.headers(), get.body());
        }
    }

    @Test
    @DisplayName("Autenticação: sem token ou com token desconhecido, 401 na v1 no formato legado")
    void unauthorizedV1() {
        for (String token : new String[] {null, "token-inexistente"}) {
            for (String prefix : List.of("", "/v1")) {
                ApiClient.Response post = api.post(prefix + "/orders", token, null,
                        "{\"customerId\":\"c-1\",\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1}]}");
                ApiClient.Response get = api.get(prefix + "/orders/1", token);

                assertThat(post.status()).isEqualTo(401);
                assertThat(get.status()).isEqualTo(401);
                ContractValidator.V1_BASELINE.assertValid("POST", "/orders", 401, post.headers(), post.body());
                ContractValidator.V1_BASELINE.assertValid("GET", "/orders/1", 401, get.headers(), get.body());
            }
        }
    }

    @Test
    @DisplayName("API-01: pedido de outro chamador retorna 404 na v2, igual a um pedido inexistente")
    void otherCallersOrderV2() {
        JsonNode order = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(newCustomerId())).body());

        ApiClient.Response response = api.get("/v2/orders/" + order.path("id").asString(), TOKEN_BRUNO);
        ApiClient.Response missing = api.get("/v2/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70", TOKEN_BRUNO);

        assertThat(response.status()).isEqualTo(404);
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree(missing.body()));
        assertThat(api.get("/v2/orders/" + order.path("id").asString(), TOKEN_ANA).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("API-02: na v1, legacyId de outro chamador retorna 404, sem enumeração por ID sequencial")
    void otherCallersOrderV1() {
        JsonNode order = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(newCustomerId())).body());
        long legacyId = order.path("legacyId").asLong();

        for (String prefix : List.of("", "/v1")) {
            ApiClient.Response response = api.get(prefix + "/orders/" + legacyId, TOKEN_ERP);

            assertThat(response.status()).isEqualTo(404);
            ContractValidator.V1_BASELINE.assertValid("GET", "/orders/" + legacyId, 404, response.headers(),
                    response.body());
        }
        assertThat(api.get("/orders/" + legacyId, TOKEN_ANA).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("API-03: status e unitPrice enviados no corpo da v2 são ignorados")
    void massAssignmentV2() {
        String body = """
                {"status":"SHIPPED","total":{"amount":"0.01","currency":"BRL"},
                 "customer":{"customerId":"%s","name":"Cliente Sintético","email":"cliente@exemplo.com"},
                 "items":[{"sku":"SKU-0042","quantity":1,"unitPrice":{"amount":"0.01","currency":"BRL"},
                           "snapshot":{"unitPrice":{"amount":"0.01","currency":"BRL"}}}]}"""
                .formatted(newCustomerId());

        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, newKey(), body);

        assertThat(response.status()).isEqualTo(201);
        JsonNode order = JSON.readTree(response.body());
        assertThat(order.path("status").asString()).isEqualTo("CREATED");
        assertThat(order.path("items").get(0).path("snapshot").path("unitPrice").path("amount").asString())
                .isEqualTo("115.00");
        assertThat(order.path("total").path("amount").asString()).isEqualTo("115.00");
        assertThat(jdbc.sql("SELECT status, total_amount::text FROM orders WHERE id = ?::uuid")
                .param(order.path("id").asString())
                .query((rs, n) -> rs.getString(1) + " " + rs.getString(2)).single())
                .isEqualTo("CREATED 115.0000");
    }

    @Test
    @DisplayName("API-03: status e price enviados no corpo da v1 são ignorados")
    void massAssignmentV1() {
        ApiClient.Response response = api.post("/orders", TOKEN_ERP, null, """
                {"customerId":"%s","status":"DELIVERED","total":0.01,
                 "items":[{"sku":"SKU-0042","quantity":1,"price":0.01}]}""".formatted(newCustomerId()));

        assertThat(response.status()).isEqualTo(201);
        JsonNode order = JSON.readTree(response.body());
        assertThat(order.path("status").asString()).isEqualTo("CREATED");
        assertThat(order.path("items").get(0).path("price").decimalValue()).isEqualByComparingTo("115.00");
        assertThat(order.path("total").decimalValue()).isEqualByComparingTo("115.00");
    }
}
