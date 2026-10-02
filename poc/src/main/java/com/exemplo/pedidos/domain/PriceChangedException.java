package com.exemplo.pedidos.domain;

import java.util.List;

/** O total esperado pelo chamador diverge do total calculado acima da tolerância (P-DOM-05). */
public class PriceChangedException extends RuntimeException {

    private final List<CatalogItemView> currentItems;

    public PriceChangedException(List<CatalogItemView> currentItems) {
        super("O preço de um ou mais itens mudou");
        this.currentItems = List.copyOf(currentItems);
    }

    public List<CatalogItemView> currentItems() {
        return currentItems;
    }
}
