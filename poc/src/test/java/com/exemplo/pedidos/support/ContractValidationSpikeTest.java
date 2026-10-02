package com.exemplo.pedidos.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova que o validador aplica a semântica OpenAPI 3.1 (JSON Schema 2020-12) aos construtos
 * usados de fato pelos contratos. Se este teste falhar, o plano B das notas de implementação
 * (swagger-parser + networknt) substitui o validador.
 */
class ContractValidationSpikeTest {

    private static final Map<String, List<String>> JSON =
            Map.of("Content-Type", List.of("application/json"));
    private static final Map<String, List<String>> PROBLEM =
            Map.of("Content-Type", List.of("application/problem+json"));

    private static final String ORDER = """
            {"id":"0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70","legacyId":1,"country":"BR",
             "status":"CREATED","version":1,"channel":"WEB",
             "customer":{"customerId":"c-1","name":"Ana","email":"ana@exemplo.com"},
             "items":[{"sku":"SKU-0001","quantity":1,"snapshot":{
                "unitPrice":{"amount":"%s","currency":"BRL"},"description":"Item",
                "catalogVersion":1,"capturedAt":"2026-10-02T10:00:00Z","snapshotSource":"CAPTURED"}}],
             "total":{"amount":"10.00","currency":"BRL"},
             "createdAt":"2026-10-02T10:00:00Z","updatedAt":"2026-10-02T10:00:00Z"}
            """;

    @Test
    @DisplayName("Pedido válido segundo a v2 é aceito")
    void validOrder() {
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                200, JSON, ORDER.formatted("10.00")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("pattern de Money.amount é aplicado")
    void moneyPattern() {
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                200, JSON, ORDER.formatted("10,00")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("format uuid é aplicado")
    void uuidFormat() {
        String body = ORDER.formatted("10.00").replace("0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70", "nao-e-uuid");
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                200, JSON, body).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("type com array [string, null] (OpenAPI 3.1) aceita null e rejeita número")
    void typeArrayWithNull() {
        String withNull = "{\"items\":[],\"nextCursor\":null}";
        String withNumber = "{\"items\":[],\"nextCursor\":123}";
        assertThat(ContractValidator.V2.validate("GET", "/orders", 200, JSON, withNull).hasErrors()).isFalse();
        assertThat(ContractValidator.V2.validate("GET", "/orders", 200, JSON, withNumber).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Status não declarado e Content-Type errado são rejeitados")
    void statusAndMediaType() {
        String problem = "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}";
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                404, PROBLEM, problem).hasErrors()).isFalse();
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                418, PROBLEM, problem).hasErrors()).isTrue();
        assertThat(ContractValidator.V2.validate("GET", "/orders/0192f7a4-3c2e-7b10-9f4d-2a6b8c1e5d70",
                404, JSON, problem).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Linha de base v1 exige campos obrigatórios")
    void baselineRequired() {
        String order = "{\"id\":1,\"customerId\":\"c-1\",\"status\":\"CREATED\",\"total\":10.0,"
                + "\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1,\"price\":10.0}],"
                + "\"createdAt\":\"2026-10-02T10:00:00Z\"}";
        assertThat(ContractValidator.V1_BASELINE.validate("GET", "/orders/1", 200, JSON, order)
                .hasErrors()).isFalse();
        assertThat(ContractValidator.V1_BASELINE.validate("GET", "/orders/1", 200, JSON,
                order.replace("\"total\":10.0,", "")).hasErrors()).isTrue();
    }
}
