package com.exemplo.pedidos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final Map<String, CatalogItemView> CATALOG = Map.of(
            "A", new CatalogItemView("BR", "A", Money.of("12.5000", "BRL"), "Item A", true, 3),
            "B", new CatalogItemView("BR", "B", Money.of("0.10", "BRL"), "Item B", true, 1),
            "X", new CatalogItemView("BR", "X", Money.of("1.00", "BRL"), "Fora de venda", false, 1));

    @Test
    @DisplayName("Total é a soma de preço × quantidade em decimal exato")
    void totalIsExact() {
        Order order = create(new OrderLine("A", 2), new OrderLine("B", 3));

        assertThat(order.total()).isEqualTo(Money.of("25.30", "BRL"));
        assertThat(order.total().scaledAmount().toPlainString()).isEqualTo("25.30");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-004: snapshot captura preço, descrição e versão do catálogo")
    void snapshotCaptured() {
        ItemSnapshot snapshot = create(new OrderLine("A", 1)).items().getFirst().snapshot();

        assertThat(snapshot.unitPrice()).isEqualTo(Money.of("12.50", "BRL"));
        assertThat(snapshot.description()).isEqualTo("Item A");
        assertThat(snapshot.catalogVersion()).isEqualTo(3);
        assertThat(snapshot.capturedAt()).isEqualTo(NOW);
        assertThat(snapshot.snapshotSource()).isEqualTo(SnapshotSource.CAPTURED);
    }

    @Test
    @DisplayName("SKU inexistente ou não vendável é recusado")
    void unknownSku() {
        assertThatThrownBy(() -> create(new OrderLine("A", 1), new OrderLine("X", 1), new OrderLine("Z", 1)))
                .isInstanceOfSatisfying(UnknownSkuException.class,
                        e -> assertThat(e.skus()).containsExactly("X", "Z"));
    }

    @Test
    @DisplayName("P-DOM-05: total esperado dentro da tolerância é aceito; fora dela, recusado")
    void expectedTotal() {
        Order order = create(new OrderLine("A", 1));

        order.ensureExpectedTotal(null, BigDecimal.ZERO, CATALOG);
        order.ensureExpectedTotal(Money.of("12.50", "BRL"), BigDecimal.ZERO, CATALOG);
        order.ensureExpectedTotal(Money.of("12.45", "BRL"), new BigDecimal("0.05"), CATALOG);
        assertThatThrownBy(() -> order.ensureExpectedTotal(Money.of("12.49", "BRL"), BigDecimal.ZERO, CATALOG))
                .isInstanceOfSatisfying(PriceChangedException.class,
                        e -> assertThat(e.currentItems()).extracting(CatalogItemView::sku).containsExactly("A"));
        assertThatThrownBy(() -> order.ensureExpectedTotal(Money.of("12.50", "USD"), BigDecimal.ZERO, CATALOG))
                .isInstanceOf(PriceChangedException.class);
    }

    private static Order create(OrderLine... lines) {
        return Order.create(new Order.NewOrder(UUID.randomUUID(), 1, "BR", "caller", null, null, Channel.WEB,
                new Customer("c-1", null, null), List.of(lines)), CATALOG, NOW);
    }
}
