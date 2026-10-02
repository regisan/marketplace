package com.exemplo.pedidos.domain;

import java.util.Objects;

/** Linha do pedido com SKU, quantidade e snapshot. */
public record OrderItem(int lineNo, String sku, int quantity, ItemSnapshot snapshot) {

    public OrderItem {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(snapshot, "snapshot");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity deve ser >= 1");
        }
    }

    public Money subtotal() {
        return snapshot.unitPrice().times(quantity);
    }
}
