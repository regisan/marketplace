package com.exemplo.pedidos.application;

/** A chave já foi usada pelo chamador com um conteúdo diferente (ADR-005, QA-INT-03). */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("Idempotency-Key já usada com outro conteúdo");
    }
}
