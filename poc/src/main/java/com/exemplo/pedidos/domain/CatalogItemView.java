package com.exemplo.pedidos.domain;

/** Visão local de catálogo usada para validar e precificar itens (ADR-003). */
public record CatalogItemView(String country, String sku, Money price, String description,
        boolean sellable, long catalogVersion) {
}
