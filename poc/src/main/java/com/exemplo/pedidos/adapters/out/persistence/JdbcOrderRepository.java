package com.exemplo.pedidos.adapters.out.persistence;

import com.exemplo.pedidos.application.ExternalReferenceTakenException;
import com.exemplo.pedidos.application.LockTimeoutException;
import com.exemplo.pedidos.application.OrderRepository;
import com.exemplo.pedidos.domain.Channel;
import com.exemplo.pedidos.domain.Customer;
import com.exemplo.pedidos.domain.ItemSnapshot;
import com.exemplo.pedidos.domain.Money;
import com.exemplo.pedidos.domain.Order;
import com.exemplo.pedidos.domain.OrderItem;
import com.exemplo.pedidos.domain.OrderStatus;
import com.exemplo.pedidos.domain.SnapshotSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderRepository implements OrderRepository {

    static final String EXTERNAL_REFERENCE_CONSTRAINT = "orders_partner_external_reference_uk";

    private static final String SELECT_ORDER = """
            SELECT id, legacy_id, country, caller_id, partner_id, external_reference, channel, status, version,
                   customer_id, customer_name, customer_email, total_amount, currency, created_at, updated_at
            FROM orders""";

    private final JdbcClient jdbc;

    JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nextLegacyId() {
        return jdbc.sql("SELECT nextval('orders_legacy_id_seq')").query(Long.class).single();
    }

    @Override
    public void insert(Order order) {
        try {
            jdbc.sql("""
                            INSERT INTO orders (id, legacy_id, country, caller_id, partner_id, external_reference,
                                channel, status, version, customer_id, customer_name, customer_email,
                                total_amount, currency, created_at, updated_at)
                            VALUES (:id, :legacyId, :country, :callerId, :partnerId, :externalReference,
                                :channel, :status, :version, :customerId, :customerName, :customerEmail,
                                :totalAmount, :currency, :createdAt, :updatedAt)""")
                    .param("id", order.id())
                    .param("legacyId", order.legacyId())
                    .param("country", order.country())
                    .param("callerId", order.callerId())
                    .param("partnerId", order.partnerId())
                    .param("externalReference", order.externalReference())
                    .param("channel", order.channel().name())
                    .param("status", order.status().name())
                    .param("version", order.version())
                    .param("customerId", order.customer().customerId())
                    .param("customerName", order.customer().name())
                    .param("customerEmail", order.customer().email())
                    .param("totalAmount", order.total().amount())
                    .param("currency", order.total().currency())
                    .param("createdAt", utc(order.createdAt()))
                    .param("updatedAt", utc(order.updatedAt()))
                    .update();
        } catch (DataAccessException e) {
            if (PostgresErrors.violates(e, EXTERNAL_REFERENCE_CONSTRAINT)) {
                throw new ExternalReferenceTakenException(e);
            }
            // Corrida com outra criação da mesma externalReference ainda não confirmada.
            if (PostgresErrors.hasState(e, PostgresErrors.LOCK_NOT_AVAILABLE)) {
                throw new LockTimeoutException(e);
            }
            throw e;
        }
        for (OrderItem item : order.items()) {
            ItemSnapshot snapshot = item.snapshot();
            jdbc.sql("""
                            INSERT INTO order_items (order_id, line_no, sku, quantity, unit_price, currency,
                                description, catalog_version, captured_at, snapshot_source)
                            VALUES (:orderId, :lineNo, :sku, :quantity, :unitPrice, :currency,
                                :description, :catalogVersion, :capturedAt, :snapshotSource)""")
                    .param("orderId", order.id())
                    .param("lineNo", item.lineNo())
                    .param("sku", item.sku())
                    .param("quantity", item.quantity())
                    .param("unitPrice", snapshot.unitPrice().amount())
                    .param("currency", snapshot.unitPrice().currency())
                    .param("description", snapshot.description())
                    .param("catalogVersion", snapshot.catalogVersion())
                    .param("capturedAt", utc(snapshot.capturedAt()))
                    .param("snapshotSource", snapshot.snapshotSource().name())
                    .update();
        }
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql(SELECT_ORDER + " WHERE id = ?").param(id).query(this::mapOrder).optional();
    }

    @Override
    public Optional<Order> findByLegacyId(long legacyId) {
        return jdbc.sql(SELECT_ORDER + " WHERE legacy_id = ?").param(legacyId).query(this::mapOrder).optional();
    }

    @Override
    public Optional<UUID> findIdByExternalReference(String partnerId, String externalReference) {
        return jdbc.sql("SELECT id FROM orders WHERE partner_id = ? AND external_reference = ?")
                .params(partnerId, externalReference)
                .query(UUID.class)
                .optional();
    }

    private Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new Order(
                id,
                rs.getLong("legacy_id"),
                rs.getString("country"),
                rs.getString("caller_id"),
                rs.getString("partner_id"),
                rs.getString("external_reference"),
                Channel.valueOf(rs.getString("channel")),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getInt("version"),
                new Customer(rs.getString("customer_id"), rs.getString("customer_name"),
                        rs.getString("customer_email")),
                findItems(id),
                new Money(rs.getBigDecimal("total_amount"), rs.getString("currency")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private List<OrderItem> findItems(UUID orderId) {
        return jdbc.sql("""
                        SELECT line_no, sku, quantity, unit_price, currency, description, catalog_version,
                               captured_at, snapshot_source
                        FROM order_items WHERE order_id = ? ORDER BY line_no""")
                .param(orderId)
                .query((rs, n) -> new OrderItem(
                        rs.getInt("line_no"),
                        rs.getString("sku"),
                        rs.getInt("quantity"),
                        new ItemSnapshot(
                                new Money(rs.getBigDecimal("unit_price"), rs.getString("currency")),
                                rs.getString("description"),
                                rs.getLong("catalog_version"),
                                instant(rs, "captured_at"),
                                SnapshotSource.valueOf(rs.getString("snapshot_source")))))
                .list();
    }

    static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
