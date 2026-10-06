package com.exemplo.pedidos.adapters.in.web.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Corpo de {@code POST /v2/orders} ({@code CreateOrderRequest} em orders-v2.yaml). Campos não
 * previstos, como {@code status} ou {@code unitPrice}, são ignorados (API-03).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CreateOrderRequestV2(CustomerV2 customer, List<Item> items, MoneyV2 expectedTotal, String externalReference) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String sku, @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer quantity) {
    }
}
