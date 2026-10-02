package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.OrderCreated;

/** Porta do transactional outbox (ADR-006). A publicação no broker fica fora da PoC. */
public interface OutboxRepository {

    /** Grava o evento na transação corrente, junto da mudança de estado que o originou. */
    void append(OrderCreated event);
}
