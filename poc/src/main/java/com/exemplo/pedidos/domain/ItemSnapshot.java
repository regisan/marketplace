package com.exemplo.pedidos.domain;

import java.time.Instant;
import java.util.Objects;

/** Cópia imutável dos dados de catálogo no momento da criação do pedido (ADR-004). */
public record ItemSnapshot(Money unitPrice, String description, long catalogVersion,
        Instant capturedAt, SnapshotSource snapshotSource) {

    public ItemSnapshot {
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(snapshotSource, "snapshotSource");
    }

    public static ItemSnapshot capture(CatalogItemView view, Instant capturedAt) {
        return new ItemSnapshot(view.price(), view.description(), view.catalogVersion(),
                capturedAt, SnapshotSource.CAPTURED);
    }
}
