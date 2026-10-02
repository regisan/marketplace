package com.exemplo.pedidos.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Agregado Pedido. Itens e snapshots são imutáveis após a criação (invariante 2). */
public record Order(
        UUID id,
        long legacyId,
        String country,
        String callerId,
        String partnerId,
        String externalReference,
        Channel channel,
        OrderStatus status,
        int version,
        Customer customer,
        List<OrderItem> items,
        Money total,
        Instant createdAt,
        Instant updatedAt) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(callerId, "callerId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(total, "total");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Pedido sem itens");
        }
        items = List.copyOf(items);
    }

    /**
     * Cria um pedido no status {@code CREATED}, precificado e com snapshot de cada item a partir
     * da visão de catálogo do país.
     *
     * @throws UnknownSkuException se algum SKU não existir ou não estiver à venda
     */
    public static Order create(NewOrder request, Map<String, CatalogItemView> catalog, Instant now) {
        List<String> unknown = request.lines().stream()
                .map(OrderLine::sku)
                .filter(sku -> !catalog.containsKey(sku) || !catalog.get(sku).sellable())
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownSkuException(unknown);
        }

        List<OrderItem> items = new ArrayList<>();
        int lineNo = 1;
        for (OrderLine line : request.lines()) {
            ItemSnapshot snapshot = ItemSnapshot.capture(catalog.get(line.sku()), now);
            items.add(new OrderItem(lineNo++, line.sku(), line.quantity(), snapshot));
        }

        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money::plus)
                .orElseThrow();

        return new Order(request.id(), request.legacyId(), request.country(), request.callerId(),
                request.partnerId(), request.externalReference(), request.channel(), OrderStatus.CREATED,
                1, request.customer(), items, total, now, now);
    }

    /**
     * Compara o total exibido ao comprador com o total calculado (P-DOM-05). O valor esperado é
     * apenas comparado, nunca usado como preço.
     *
     * @throws PriceChangedException se a diferença passar da tolerância ou a moeda divergir
     */
    public void ensureExpectedTotal(Money expectedTotal, BigDecimal tolerance, Map<String, CatalogItemView> catalog) {
        if (expectedTotal == null) {
            return;
        }
        if (!expectedTotal.sameCurrencyAs(total) || expectedTotal.distanceTo(total).compareTo(tolerance) > 0) {
            Set<String> skus = new LinkedHashSet<>();
            items.forEach(item -> skus.add(item.sku()));
            throw new PriceChangedException(skus.stream().map(catalog::get).toList());
        }
    }

    public boolean belongsTo(String caller) {
        return callerId.equals(caller);
    }

    /** Dados de entrada para {@link #create}: identidade já gerada e dados validados da requisição. */
    public record NewOrder(UUID id, long legacyId, String country, String callerId, String partnerId,
            String externalReference, Channel channel, Customer customer, List<OrderLine> lines) {
    }
}
