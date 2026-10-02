package com.exemplo.pedidos.application;

/** Violação de unicidade em (partner_id, external_reference), sinalizada pela persistência. */
public class ExternalReferenceTakenException extends RuntimeException {

    public ExternalReferenceTakenException(Throwable cause) {
        super("externalReference já usada pelo parceiro", cause);
    }
}
