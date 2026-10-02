package com.exemplo.pedidos.application;

import java.util.Objects;

/**
 * Chave de idempotência enviada pelo chamador e hash da requisição (método, operação e corpo
 * canonizado). O escopo é o par (chamador, chave) (ADR-005).
 */
public record IdempotencyRequest(String key, String requestHash) {

    public IdempotencyRequest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
    }
}
