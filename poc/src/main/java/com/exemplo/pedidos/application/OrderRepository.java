package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.Order;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de pedidos. */
public interface OrderRepository {

    long nextLegacyId();

    /**
     * Grava pedido e itens na transação corrente.
     *
     * @throws ExternalReferenceTakenException se (parceiro, externalReference) já existir
     */
    void insert(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByLegacyId(long legacyId);

    Optional<UUID> findIdByExternalReference(String partnerId, String externalReference);
}
