package com.exemplo.pedidos.application;

/** A transação esperou um lock além do {@code lock_timeout}. */
public class LockTimeoutException extends RuntimeException {

    public LockTimeoutException(Throwable cause) {
        super("Tempo de espera por lock excedido", cause);
    }
}
