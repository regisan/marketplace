package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.Channel;
import java.util.Objects;

/**
 * Chamador autenticado. {@code callerId} é o escopo da chave de idempotência e o dono dos
 * pedidos que cria (ADR-005, API-01).
 */
public record Caller(String callerId, CallerType type, String partnerId, Channel channel, String country) {

    public Caller {
        Objects.requireNonNull(callerId, "callerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(country, "country");
        if (type == CallerType.PARTNER && partnerId == null) {
            throw new IllegalArgumentException("Parceiro sem partnerId");
        }
    }

    public boolean isPartner() {
        return type == CallerType.PARTNER;
    }
}
