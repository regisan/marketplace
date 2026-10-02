package com.exemplo.pedidos.application;

import java.util.UUID;

/** O parceiro já criou um pedido com esta externalReference (ADR-005, QA-INT-06). */
public class DuplicateExternalReferenceException extends RuntimeException {

    private final UUID existingOrderId;

    public DuplicateExternalReferenceException(UUID existingOrderId) {
        super("Já existe um pedido com esta referência externa");
        this.existingOrderId = existingOrderId;
    }

    public UUID existingOrderId() {
        return existingOrderId;
    }
}
