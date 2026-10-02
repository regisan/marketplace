package com.exemplo.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exemplo.pedidos.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    @DisplayName("P-POC-03: catálogo sintético com 1.000 SKUs no BR, alguns não vendáveis")
    void syntheticCatalog() {
        assertThat(jdbc.sql("SELECT count(*) FROM catalog_item_view WHERE country = 'BR'")
                .query(Integer.class).single()).isEqualTo(1000);
        assertThat(jdbc.sql("SELECT count(*) FROM catalog_item_view WHERE NOT sellable")
                .query(Integer.class).single()).isEqualTo(10);
    }

    @Test
    @DisplayName("ADR-005: (partner_id, external_reference) é único só para parceiros")
    void partnerExternalReferenceUnique() {
        tx.executeWithoutResult(status -> {
            insertOrder("acme", "MKT-1");
            assertThatThrownBy(() -> insertOrder("acme", "MKT-1")).isInstanceOf(DuplicateKeyException.class);
            status.setRollbackOnly();
        });
        tx.executeWithoutResult(status -> {
            insertOrder(null, "MKT-1");
            insertOrder(null, "MKT-1");
            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("ADR-004: UPDATE em order_items é rejeitado")
    void orderItemsImmutable() {
        tx.executeWithoutResult(status -> {
            UUID id = insertOrder(null, null);
            jdbc.sql("""
                    INSERT INTO order_items (order_id, line_no, sku, quantity, unit_price, currency,
                        description, catalog_version, captured_at, snapshot_source)
                    VALUES (?, 1, 'SKU-0001', 1, 10, 'BRL', 'x', 1, now(), 'CAPTURED')""")
                    .param(id).update();
            assertThatThrownBy(() -> jdbc.sql("UPDATE order_items SET unit_price = 1 WHERE order_id = ?")
                    .param(id).update())
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immutable");
            status.setRollbackOnly();
        });
    }

    private UUID insertOrder(String partnerId, String externalReference) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO orders (id, legacy_id, country, caller_id, partner_id, external_reference,
                    channel, status, version, customer_id, total_amount, currency, created_at, updated_at)
                VALUES (?, nextval('orders_legacy_id_seq'), 'BR', 'teste', ?, ?, 'WEB', 'CREATED', 1,
                    'c-1', 10, 'BRL', now(), now())""")
                .params(id, partnerId, externalReference).update();
        return id;
    }
}
