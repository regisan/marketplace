package com.exemplo.pedidos.v1;

import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.TOKEN_ERP;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.consumidor.ConsumidorV1;
import com.exemplo.pedidos.consumidor.ConsumidorV1.ItemSolicitado;
import com.exemplo.pedidos.consumidor.ConsumidorV1.Pedido;
import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.ContractValidator;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-007 e FF-13: a v1 evoluída (1.1.0) não quebra o consumidor escrito para a 1.0.0, e v1 e v2
 * são adaptadores do mesmo domínio.
 */
@IntegrationTest
class CompatibilidadeV1IT {

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
    @DisplayName("QA-COM-01: consumidor da 1.0.0 cria e consulta pedido sem header; respostas válidas na linha de base")
    void baselineConsumerCreatesAndReads() {
        ConsumidorV1 consumidor = new ConsumidorV1("http://localhost:" + port, TOKEN_ERP);
        String customerId = newCustomerId();

        ConsumidorV1.Resposta criacao = consumidor.criarPedido(customerId,
                List.of(new ItemSolicitado("SKU-0001", 2), new ItemSolicitado("SKU-0042", 1)));

        assertThat(criacao.status()).isEqualTo(201);
        ContractValidator.V1_BASELINE.assertValid("POST", "/orders", 201, criacao.headers(), criacao.corpo());
        Pedido criado = criacao.pedido();
        assertThat(criado.customerId()).isEqualTo(customerId);
        assertThat(criado.status()).isEqualTo("CREATED");
        assertThat(criado.total()).isEqualByComparingTo("140.00");
        assertThat(criado.items()).extracting(ConsumidorV1.ItemPedido::price)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("12.50"), new BigDecimal("115.00"));

        ConsumidorV1.Resposta consulta = consumidor.consultarPedido(criado.id());

        assertThat(consulta.status()).isEqualTo(200);
        ContractValidator.V1_BASELINE.assertValid("GET", "/orders/" + criado.id(), 200, consulta.headers(),
                consulta.corpo());
        assertThat(consulta.pedido()).isEqualTo(criado);
    }

    @Test
    @DisplayName("QA-COM-01: sem Idempotency-Key, o comportamento da 1.0.0 é mantido e nenhum registro é criado")
    void withoutKeyBehavesAsBaseline() {
        ConsumidorV1 consumidor = new ConsumidorV1("http://localhost:" + port, TOKEN_ERP);
        String customerId = newCustomerId();
        List<ItemSolicitado> itens = List.of(new ItemSolicitado("SKU-0001", 1));

        ConsumidorV1.Resposta primeira = consumidor.criarPedido(customerId, itens);
        ConsumidorV1.Resposta segunda = consumidor.criarPedido(customerId, itens);

        assertThat(primeira.status()).isEqualTo(201);
        assertThat(segunda.status()).isEqualTo(201);
        assertThat(segunda.pedido().id()).isNotEqualTo(primeira.pedido().id());
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM idempotency_record r JOIN orders o ON o.id = r.order_id
                        WHERE o.customer_id = ?""")
                .param(customerId).query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("QA-COM-01: erro de validação continua no formato da 1.0.0")
    void baselineErrorFormat() {
        ConsumidorV1 consumidor = new ConsumidorV1("http://localhost:" + port, TOKEN_ERP);

        ConsumidorV1.Resposta resposta = consumidor.criarPedido(newCustomerId(), List.of(new ItemSolicitado("SKU-9999", 1)));

        assertThat(resposta.status()).isEqualTo(400);
        ContractValidator.V1_BASELINE.assertValid("POST", "/orders", 400, resposta.headers(), resposta.corpo());
        assertThat(resposta.erro().message()).contains("SKU-9999");
    }

    @Test
    @DisplayName("QA-COM-03: pedido criado pela v2 é consultado pela v1 pelo legacyId, válido na linha de base")
    void v2OrderReadThroughV1() {
        JsonNode v2 = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(newCustomerId())).body());
        long legacyId = v2.path("legacyId").asLong();

        for (String path : List.of("/orders/", "/v1/orders/")) {
            ApiClient.Response response = api.get(path + legacyId, TOKEN_ANA);

            assertThat(response.status()).isEqualTo(200);
            ContractValidator.V1_BASELINE.assertValid("GET", "/orders/" + legacyId, 200, response.headers(),
                    response.body());
            JsonNode v1 = JSON.readTree(response.body());
            assertThat(v1.path("id").asLong()).isEqualTo(legacyId);
            assertThat(v1.path("customerId")).isEqualTo(v2.path("customer").path("customerId"));
            assertThat(v1.path("total").decimalValue()).isEqualByComparingTo(v2.path("total").path("amount").asString());
            assertThat(v1.path("items").get(1).path("price").decimalValue()).isEqualByComparingTo("115.00");
            assertThat(v1.path("items").get(1).path("description").asString()).isEqualTo("Produto sintético 0042");
            assertThat(v1.has("currency")).isFalse();
        }
    }

    @Test
    @DisplayName("ADR-005, ADR-007: v1 com Idempotency-Key repetida cria 1 pedido; /orders e /v1/orders são a mesma operação")
    void v1WithRepeatedKey() {
        String customerId = newCustomerId();
        String key = newKey();
        String body = "{\"customerId\":\"%s\",\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1}]}".formatted(customerId);

        ApiClient.Response first = api.post("/orders", TOKEN_ERP, key, body);
        ApiClient.Response second = api.post("/v1/orders", TOKEN_ERP, key, body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(JSON.readTree(second.body())).isEqualTo(JSON.readTree(first.body()));
        ContractValidator.V1.assertValid("POST", "/orders", 201, second.headers(), second.body());
        assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE customer_id = ?").param(customerId)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-005, ADR-007: a mesma chave na v1 e na v2 não é tratada como repetição")
    void sameKeyAcrossVersions() {
        String customerId = newCustomerId();
        String key = newKey();
        api.post("/orders", TOKEN_ANA, key,
                "{\"customerId\":\"%s\",\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1}]}".formatted(customerId));

        ApiClient.Response v2 = api.post("/v2/orders", TOKEN_ANA, key, Requests.v2(customerId));

        assertThat(v2.status()).isEqualTo(422);
        ContractValidator.V2.assertValid("POST", "/orders", 422, v2.headers(), v2.body());
    }

    @Test
    @DisplayName("ADR-005: na v1, chave com corpo diferente retorna 422 no formato v1")
    void v1KeyReuse() {
        String key = newKey();
        api.post("/orders", TOKEN_ERP, key, "{\"customerId\":\"c-1\",\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1}]}");

        ApiClient.Response response = api.post("/orders", TOKEN_ERP, key,
                "{\"customerId\":\"c-1\",\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":2}]}");

        assertThat(response.status()).isEqualTo(422);
        ContractValidator.V1.assertValid("POST", "/orders", 422, response.headers(), response.body());
    }
}
