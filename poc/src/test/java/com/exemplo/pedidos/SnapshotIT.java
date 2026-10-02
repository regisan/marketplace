package com.exemplo.pedidos;

import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** QA-AUD-01 e ADR-004: preço e descrição vendidos não mudam quando o catálogo muda. */
@IntegrationTest
class SnapshotIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** SKU exclusivo deste teste: 10,00 + 27 × 2,50 = 77,50. */
    private static final String SKU = "SKU-0777";

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @AfterEach
    void restoreCatalog() {
        jdbc.sql("""
                UPDATE catalog_item_view SET price = 77.50, description = 'Produto sintético 0777', catalog_version = 1
                WHERE country = 'BR' AND sku = ?""").param(SKU).update();
    }

    @Test
    @DisplayName("QA-AUD-01: após mudar preço e descrição no read model, o pedido existente mantém os valores na v1 e na v2")
    void snapshotSurvivesCatalogChange() {
        JsonNode created = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(),
                Requests.v2WithSku(newCustomerId(), SKU)).body());
        String orderId = created.path("id").asString();

        jdbc.sql("""
                UPDATE catalog_item_view SET price = 99.90, description = 'Descrição nova', catalog_version = 2
                WHERE country = 'BR' AND sku = ?""").param(SKU).update();

        JsonNode snapshot = JSON.readTree(api.get("/v2/orders/" + orderId, TOKEN_ANA).body())
                .path("items").get(0).path("snapshot");
        assertThat(snapshot.path("unitPrice").path("amount").asString()).isEqualTo("77.50");
        assertThat(snapshot.path("unitPrice").path("currency").asString()).isEqualTo("BRL");
        assertThat(snapshot.path("description").asString()).isEqualTo("Produto sintético 0777");
        assertThat(snapshot.path("catalogVersion").asLong()).isEqualTo(1);
        assertThat(snapshot.path("snapshotSource").asString()).isEqualTo("CAPTURED");

        JsonNode v1Item = JSON.readTree(api.get("/orders/" + created.path("legacyId").asLong(), TOKEN_ANA).body())
                .path("items").get(0);
        assertThat(v1Item.path("price").decimalValue()).isEqualByComparingTo("77.50");
        assertThat(v1Item.path("description").asString()).isEqualTo("Produto sintético 0777");

        JsonNode newOrder = JSON.readTree(api.post("/v2/orders", TOKEN_ANA, newKey(),
                Requests.v2WithSku(newCustomerId(), SKU)).body());
        assertThat(newOrder.path("items").get(0).path("snapshot").path("unitPrice").path("amount").asString())
                .isEqualTo("99.90");
    }
}
