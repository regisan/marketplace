package com.exemplo.pedidos.adapters.in.web.v1;

import com.exemplo.pedidos.domain.Order;
import com.exemplo.pedidos.domain.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code Order} da v1: identificador sequencial ({@code legacyId}) e preço numérico sem moeda,
 * agora lidos do snapshot do item (ADR-004, ADR-007).
 */
record OrderResponseV1(long id, String customerId, String status, BigDecimal total, List<Item> items,
        Instant createdAt) {

    static OrderResponseV1 from(Order order) {
        return new OrderResponseV1(
                order.legacyId(),
                order.customer().customerId(),
                order.status().name(),
                order.total().scaledAmount(),
                order.items().stream().map(Item::from).toList(),
                order.createdAt());
    }

    record Item(String sku, int quantity, BigDecimal price, String description) {

        static Item from(OrderItem item) {
            return new Item(item.sku(), item.quantity(), item.snapshot().unitPrice().scaledAmount(),
                    item.snapshot().description());
        }
    }
}
