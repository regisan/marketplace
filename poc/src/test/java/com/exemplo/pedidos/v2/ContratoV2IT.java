package com.exemplo.pedidos.v2;

import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.ContractValidator;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ADR-007: respostas da v2 válidas segundo orders-v2.yaml. */
@IntegrationTest
class ContratoV2IT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    @DisplayName("ADR-007: 201 na criação e 200 na consulta, ambos válidos; consulta igual à criação")
    void createdAndFetched() {
        ApiClient.Response created = api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(newCustomerId(), "140.00", null));

        assertThat(created.status()).isEqualTo(201);
        assertValid("POST", "/orders", created);
        JsonNode order = JSON.readTree(created.body());
        assertThat(order.path("total").path("amount").asString()).isEqualTo("140.00");
        assertThat(order.path("items").get(0).path("snapshot").path("unitPrice").path("amount").asString())
                .isEqualTo("12.50");
        assertThat(order.path("status").asString()).isEqualTo("CREATED");
        assertThat(order.path("channel").asString()).isEqualTo("WEB");
        assertThat(created.header("Location")).endsWith("/v2/orders/" + order.path("id").asString());

        ApiClient.Response fetched = api.get("/v2/orders/" + order.path("id").asString(), TOKEN_ANA);
        assertThat(fetched.status()).isEqualTo(200);
        assertValid("GET", "/orders/" + order.path("id").asString(), fetched);
        assertThat(JSON.readTree(fetched.body())).isEqualTo(order);
    }

    @Test
    @DisplayName("ADR-007: 400 com corpo inválido, em Problem Details")
    void badRequest() {
        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, newKey(), "{\"items\":[]}");

        assertThat(response.status()).isEqualTo(400);
        assertValid("POST", "/orders", response);
        assertThat(JSON.readTree(response.body()).path("errors").size()).isPositive();
    }

    @Test
    @DisplayName("ADR-007: 400 com JSON malformado")
    void malformedJson() {
        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, newKey(), "{");

        assertThat(response.status()).isEqualTo(400);
        assertValid("POST", "/orders", response);
    }

    @Test
    @DisplayName("ADR-007: 404 para pedido inexistente e identificador malformado")
    void notFound() {
        String missing = "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70";
        ApiClient.Response response = api.get("/v2" + missing, TOKEN_ANA);
        assertThat(response.status()).isEqualTo(404);
        assertValid("GET", missing, response);

        assertThat(api.get("/v2/orders/nao-e-uuid", TOKEN_ANA).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("ADR-007, P-DOM-05: 409 price-changed com os preços atuais")
    void priceChanged() {
        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, newKey(),
                Requests.v2(newCustomerId(), "139.99", null));

        assertThat(response.status()).isEqualTo(409);
        assertValid("POST", "/orders", response);
        JsonNode problem = JSON.readTree(response.body());
        assertThat(problem.path("type").asString()).isEqualTo("https://api.exemplo.com/problems/price-changed");
        assertThat(problem.path("currentItems").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("ADR-007: 422 unknown-sku para SKU inexistente ou não vendável")
    void unknownSku() {
        for (String sku : new String[] {"SKU-9999", "SKU-0100"}) {
            ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, newKey(),
                    Requests.v2WithSku(newCustomerId(), sku));

            assertThat(response.status()).isEqualTo(422);
            assertValid("POST", "/orders", response);
            assertThat(JSON.readTree(response.body()).path("type").asString())
                    .isEqualTo("https://api.exemplo.com/problems/unknown-sku");
        }
    }

    private static void assertValid(String method, String path, ApiClient.Response response) {
        ContractValidator.V2.assertValid(method, path, response.status(), response.headers(), response.body());
    }
}
