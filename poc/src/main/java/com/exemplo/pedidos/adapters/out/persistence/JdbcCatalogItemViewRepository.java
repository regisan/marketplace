package com.exemplo.pedidos.adapters.out.persistence;

import com.exemplo.pedidos.application.CatalogItemViewRepository;
import com.exemplo.pedidos.domain.CatalogItemView;
import com.exemplo.pedidos.domain.Money;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCatalogItemViewRepository implements CatalogItemViewRepository {

    private final JdbcClient jdbc;

    JdbcCatalogItemViewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, CatalogItemView> findBySkus(String country, Collection<String> skus) {
        if (skus.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT country, sku, price, currency, description, sellable, catalog_version
                        FROM catalog_item_view
                        WHERE country = :country AND sku IN (:skus)""")
                .param("country", country)
                .param("skus", skus)
                .query((rs, n) -> new CatalogItemView(
                        rs.getString("country"),
                        rs.getString("sku"),
                        new Money(rs.getBigDecimal("price"), rs.getString("currency")),
                        rs.getString("description"),
                        rs.getBoolean("sellable"),
                        rs.getLong("catalog_version")))
                .stream()
                .collect(Collectors.toMap(CatalogItemView::sku, Function.identity()));
    }
}
