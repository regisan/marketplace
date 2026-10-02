package com.exemplo.pedidos.adapters.in.web.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Corpo de {@code POST /orders} na v1 (inalterado desde a 1.0.0). Campos extras são ignorados. */
@JsonIgnoreProperties(ignoreUnknown = true)
record CreateOrderRequestV1(String customerId, List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String sku, Integer quantity) {
    }
}
