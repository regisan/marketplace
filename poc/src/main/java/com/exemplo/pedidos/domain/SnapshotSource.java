package com.exemplo.pedidos.domain;

/** Origem do snapshot: capturado na criação ou reconstruído por backfill (ADR-004, P-DOM-07). */
public enum SnapshotSource {
    CAPTURED, RECONSTRUCTED
}
