package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.domain.ItemSnapshot;
import com.exemplo.pedidos.domain.Order;
import com.exemplo.pedidos.domain.OrderItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code Order} de orders-v2.yaml. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record OrderResponseV2(
        UUID id,
        Long legacyId,
        String country,
        String status,
        int version,
        String channel,
        String externalReference,
        CustomerV2 customer,
        List<Item> items,
        MoneyV2 total,
        Instant createdAt,
        Instant updatedAt) {

    static OrderResponseV2 from(Order order) {
        return new OrderResponseV2(
                order.id(),
                order.legacyId(),
                order.country(),
                order.status().name(),
                order.version(),
                order.channel().name(),
                order.externalReference(),
                new CustomerV2(order.customer().customerId(), order.customer().name(), order.customer().email()),
                order.items().stream().map(Item::from).toList(),
                MoneyV2.from(order.total()),
                order.createdAt(),
                order.updatedAt());
    }

    record Item(String sku, int quantity, Snapshot snapshot) {

        static Item from(OrderItem item) {
            return new Item(item.sku(), item.quantity(), Snapshot.from(item.snapshot()));
        }
    }

    record Snapshot(MoneyV2 unitPrice, String description, long catalogVersion, Instant capturedAt,
            String snapshotSource) {

        static Snapshot from(ItemSnapshot snapshot) {
            return new Snapshot(MoneyV2.from(snapshot.unitPrice()), snapshot.description(),
                    snapshot.catalogVersion(), snapshot.capturedAt(), snapshot.snapshotSource().name());
        }
    }
}
