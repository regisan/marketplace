package com.exemplo.pedidos.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequestHasherTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final RequestHasher hasher = new RequestHasher(json);

    record Item(String sku, Integer quantity) {
    }

    record Body(String customerId, List<Item> items, String externalReference) {
    }

    @Test
    @DisplayName("Canonização: chaves ordenadas em todos os níveis, sem espaços")
    void canonicalForm() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("b", List.of(Map.of("z", 1, "a", "x")));
        body.put("a", null);

        assertThat(hasher.canonicalize(json.valueToTree(body))).isEqualTo("{\"a\":null,\"b\":[{\"a\":\"x\",\"z\":1}]}");
    }

    @Test
    @DisplayName("Mesmo conteúdo em ordens diferentes gera o mesmo hash; ordem dos itens importa")
    void orderOfKeysDoesNotMatter() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("customerId", "c-1");
        first.put("items", List.of(Map.of("sku", "A", "quantity", 1)));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("items", List.of(Map.of("quantity", 1, "sku", "A")));
        second.put("customerId", "c-1");

        assertThat(hasher.hash("POST", "createOrder", first)).isEqualTo(hasher.hash("POST", "createOrder", second));
        assertThat(hasher.hash("POST", "createOrder", new Body("c-1", List.of(new Item("A", 1), new Item("B", 1)), null)))
                .isNotEqualTo(hasher.hash("POST", "createOrder",
                        new Body("c-1", List.of(new Item("B", 1), new Item("A", 1)), null)));
    }

    @Test
    @DisplayName("Operação e método fazem parte do hash: v1 e v2 nunca se confundem")
    void operationIsPartOfHash() {
        Body body = new Body("c-1", List.of(new Item("A", 1)), null);

        assertThat(hasher.hash("POST", "createOrder", body)).isNotEqualTo(hasher.hash("POST", "createOrderV1", body));
        assertThat(hasher.hash("POST", "createOrder", body)).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("Conteúdo diferente gera hash diferente")
    void differentContent() {
        assertThat(hasher.hash("POST", "createOrder", new Body("c-1", List.of(new Item("A", 1)), null)))
                .isNotEqualTo(hasher.hash("POST", "createOrder", new Body("c-1", List.of(new Item("A", 2)), null)));
    }
}
