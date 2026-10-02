package com.exemplo.pedidos.adapters.out.persistence;

import static com.exemplo.pedidos.adapters.out.persistence.JdbcOrderRepository.utc;

import com.exemplo.pedidos.application.OutboxRepository;
import com.exemplo.pedidos.domain.OrderCreated;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcOutboxRepository implements OutboxRepository {

    static final String AGGREGATE_TYPE = "Order";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcOutboxRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(OrderCreated event) {
        jdbc.sql("""
                        INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, aggregate_version, event_type,
                            payload, occurred_at)
                        VALUES (:eventId, :aggregateType, :aggregateId, :aggregateVersion, :eventType,
                            CAST(:payload AS jsonb), :occurredAt)""")
                .param("eventId", event.eventId())
                .param("aggregateType", AGGREGATE_TYPE)
                .param("aggregateId", event.orderId())
                .param("aggregateVersion", event.orderVersion())
                .param("eventType", OrderCreated.EVENT_TYPE)
                .param("payload", json.writeValueAsString(OrderCreatedPayload.from(event)))
                .param("occurredAt", utc(event.occurredAt()))
                .update();
    }

    /**
     * Payload conforme {@code OrderCreatedPayload} de order-events.yaml. Lista explícita de campos:
     * nada do comprador entra no evento.
     */
    record OrderCreatedPayload(
            UUID eventId,
            String eventType,
            int schemaVersion,
            Instant occurredAt,
            UUID orderId,
            int orderVersion,
            String country,
            String status,
            String channel,
            String partnerId,
            String externalReference,
            Money total,
            int itemCount) {

        static OrderCreatedPayload from(OrderCreated event) {
            return new OrderCreatedPayload(event.eventId(), OrderCreated.EVENT_TYPE, OrderCreated.SCHEMA_VERSION,
                    event.occurredAt(), event.orderId(), event.orderVersion(), event.country(), event.status().name(),
                    event.channel().name(), event.partnerId(), event.externalReference(),
                    new Money(event.total().scaledAmount().toPlainString(), event.total().currency()),
                    event.itemCount());
        }

        record Money(String amount, String currency) {
        }
    }
}
