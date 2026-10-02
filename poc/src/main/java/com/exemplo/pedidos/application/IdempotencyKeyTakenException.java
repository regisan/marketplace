package com.exemplo.pedidos.application;

/** Violação de unicidade em (caller_id, idem_key), sinalizada pela persistência. */
public class IdempotencyKeyTakenException extends RuntimeException {

    public IdempotencyKeyTakenException(Throwable cause) {
        super("Idempotency-Key já registrada para o chamador", cause);
    }
}
