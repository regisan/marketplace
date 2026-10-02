package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Consulta de pedido com verificação de dono (API-01, API-02). */
@Service
public class GetOrderUseCase {

    private final OrderRepository orders;

    public GetOrderUseCase(OrderRepository orders) {
        this.orders = orders;
    }

    public Order byId(Caller caller, UUID id) {
        return owned(caller, orders.findById(id));
    }

    public Order byLegacyId(Caller caller, long legacyId) {
        return owned(caller, orders.findByLegacyId(legacyId));
    }

    private static Order owned(Caller caller, Optional<Order> order) {
        return order.filter(o -> o.belongsTo(caller.callerId())).orElseThrow(OrderNotFoundException::new);
    }
}
