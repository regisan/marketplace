package com.exemplo.pedidos.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento {@code OrderCreated} (order-events.yaml). Não contém nenhum dado do comprador: nem
 * {@code customerId}, nem nome, nem e-mail (ADR-002, QA-PRI-02).
 */
public record OrderCreated(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        int orderVersion,
        String country,
        OrderStatus status,
        Channel channel,
        String partnerId,
        String externalReference,
        Money total,
        int itemCount) {

    public static final String EVENT_TYPE = "OrderCreated";
    public static final int SCHEMA_VERSION = 1;

    public OrderCreated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
    }

    public static OrderCreated of(Order order, UUID eventId) {
        return new OrderCreated(eventId, order.createdAt(), order.id(), order.version(), order.country(),
                order.status(), order.channel(), order.partnerId(), order.externalReference(), order.total(),
                order.items().size());
    }
}
