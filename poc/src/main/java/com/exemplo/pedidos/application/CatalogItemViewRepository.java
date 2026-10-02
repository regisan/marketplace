package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.CatalogItemView;
import java.util.Collection;
import java.util.Map;

/** Porta de leitura do read model local de catálogo (ADR-003). */
public interface CatalogItemViewRepository {

    /** Itens encontrados, indexados por SKU; SKUs inexistentes ficam fora do mapa. */
    Map<String, CatalogItemView> findBySkus(String country, Collection<String> skus);
}
