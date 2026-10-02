package com.exemplo.pedidos.application;

/** Outra requisição com a mesma chave ainda está em processamento (ADR-005). */
public class RequestInProgressException extends RuntimeException {

    public static final int RETRY_AFTER_SECONDS = 1;

    public RequestInProgressException() {
        super("Requisição com a mesma chave em processamento");
    }
}
