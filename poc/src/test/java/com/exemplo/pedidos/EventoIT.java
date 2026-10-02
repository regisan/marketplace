package com.exemplo.pedidos;

import static com.exemplo.pedidos.support.Requests.TOKEN_ACME;
import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.EventContractValidator;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** QA-PRI-02 e FF-15: o OrderCreated gravado no outbox segue o contrato e não tem dados pessoais. */
@IntegrationTest
class EventoIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> CUSTOMER_FIELDS = List.of("customer", "customerId", "name", "email");

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
    @DisplayName("QA-PRI-02: OrderCreated de parceiro válido segundo order-events.yaml e sem nome, e-mail ou customerId")
    void partnerOrderEvent() {
        String customerId = newCustomerId();
        String externalReference = "MKT-" + UUID.randomUUID();
        JsonNode order = JSON.readTree(api.post("/v2/orders", TOKEN_ACME, newKey(),
                Requests.v2(customerId, null, externalReference)).body());

        String payload = outboxPayload(order.path("id").asString());
        JsonNode event = JSON.readTree(payload);

        EventContractValidator.ORDER_CREATED.assertValid(payload);
        assertNoCustomerData(event, customerId);
        assertThat(event.path("eventType").asString()).isEqualTo("OrderCreated");
        assertThat(event.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(UUID.fromString(event.path("eventId").asString()).version()).isEqualTo(7);
        assertThat(event.path("orderId")).isEqualTo(order.path("id"));
        assertThat(event.path("orderVersion").asInt()).isEqualTo(1);
        assertThat(event.path("occurredAt")).isEqualTo(order.path("createdAt"));
        assertThat(event.path("channel").asString()).isEqualTo("PARTNER");
        assertThat(event.path("partnerId").asString()).isEqualTo("acme");
        assertThat(event.path("externalReference").asString()).isEqualTo(externalReference);
        assertThat(event.path("total")).isEqualTo(order.path("total"));
        assertThat(event.path("itemCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("QA-PRI-02: OrderCreated de cliente final válido, com partnerId e externalReference nulos")
    void customerOrderEvent() {
        String customerId = newCustomerId();
        JsonNode order = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(customerId)).body());

        String payload = outboxPayload(order.path("id").asString());
        JsonNode event = JSON.readTree(payload);

        EventContractValidator.ORDER_CREATED.assertValid(payload);
        assertNoCustomerData(event, customerId);
        assertThat(event.path("channel").asString()).isEqualTo("WEB");
        assertThat(event.path("partnerId").isNull()).isTrue();
        assertThat(event.path("externalReference").isNull()).isTrue();
    }

    @Test
    @DisplayName("FF-15: o validador do contrato de eventos rejeita payload fora do schema")
    void validatorRejectsInvalidPayload() {
        String customerId = newCustomerId();
        JsonNode order = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(), Requests.v2(customerId)).body());
        ObjectNode payload = (ObjectNode) JSON.readTree(outboxPayload(order.path("id").asString()));

        assertThat(EventContractValidator.ORDER_CREATED.validate(payload.toString())).isEmpty();
        assertThat(EventContractValidator.ORDER_CREATED.validate(payload.deepCopy().put("eventType", "Outro").toString()))
                .isNotEmpty();
        assertThat(EventContractValidator.ORDER_CREATED.validate(payload.deepCopy().put("itemCount", 0).toString()))
                .isNotEmpty();
        assertThat(EventContractValidator.ORDER_CREATED.validate(payload.deepCopy().remove(List.of("orderVersion")).toString()))
                .isNotEmpty();
        ObjectNode badMoney = payload.deepCopy();
        ((ObjectNode) badMoney.path("total")).put("amount", "12,50");
        assertThat(EventContractValidator.ORDER_CREATED.validate(badMoney.toString())).isNotEmpty();
    }

    private String outboxPayload(String orderId) {
        return jdbc.sql("""
                        SELECT payload::text FROM outbox_event
                        WHERE aggregate_type = 'Order' AND aggregate_id = ?::uuid AND event_type = 'OrderCreated'""")
                .param(orderId)
                .query(String.class)
                .single();
    }

    /** Nenhuma chave de dados do comprador e nenhum valor pessoal sintético em todo o payload. */
    private static void assertNoCustomerData(JsonNode event, String customerId) {
        List<String> keys = new ArrayList<>();
        collectKeys(event, keys);
        assertThat(keys).doesNotContainAnyElementsOf(CUSTOMER_FIELDS);
        assertThat(event.toString()).doesNotContain(customerId, "Cliente Sintético", "cliente@exemplo.com");
    }

    private static void collectKeys(JsonNode node, List<String> keys) {
        node.properties().forEach(entry -> {
            keys.add(entry.getKey());
            collectKeys(entry.getValue(), keys);
        });
        if (node.isArray()) {
            node.forEach(element -> collectKeys(element, keys));
        }
    }
}
