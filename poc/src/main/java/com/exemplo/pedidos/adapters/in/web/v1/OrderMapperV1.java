package com.exemplo.pedidos.adapters.in.web.v1;

import com.exemplo.pedidos.adapters.in.web.RequestValidator;
import com.exemplo.pedidos.application.Caller;
import com.exemplo.pedidos.application.CreateOrderCommand;
import com.exemplo.pedidos.domain.Channel;
import com.exemplo.pedidos.domain.Customer;
import com.exemplo.pedidos.domain.OrderLine;
import java.util.List;

/**
 * Valida a requisição v1 segundo a linha de base 1.0.0, sem regras novas que possam recusar o que
 * um consumidor atual já envia.
 */
final class OrderMapperV1 {

    private OrderMapperV1() {
    }

    static CreateOrderCommand toCommand(Caller caller, CreateOrderRequestV1 request) {
        RequestValidator v = new RequestValidator();
        v.require(request != null, "body", "obrigatório").throwIfInvalid();

        v.requireText(request.customerId(), "customerId", 64);
        List<CreateOrderRequestV1.Item> items = request.items() == null ? List.of() : request.items();
        v.require(!items.isEmpty(), "items", "ao menos um item");
        for (int i = 0; i < items.size(); i++) {
            CreateOrderRequestV1.Item item = items.get(i);
            String field = "items[" + i + "]";
            if (item == null) {
                v.require(false, field, "obrigatório");
                continue;
            }
            v.requireText(item.sku(), field + ".sku", 64)
                    .require(item.quantity() != null && item.quantity() >= 1, field + ".quantity", "mínimo 1");
        }
        v.throwIfInvalid();

        return new CreateOrderCommand(
                caller,
                Channel.LEGACY_V1,
                new Customer(request.customerId(), null, null),
                items.stream().map(item -> new OrderLine(item.sku(), item.quantity())).toList(),
                null,
                null);
    }
}
