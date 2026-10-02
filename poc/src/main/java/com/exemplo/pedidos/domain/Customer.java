package com.exemplo.pedidos.domain;

import java.util.Objects;

/**
 * Comprador do pedido. Nome e e-mail são dados pessoais e opcionais no domínio: a v1 envia
 * apenas {@code customerId}.
 */
public record Customer(String customerId, String name, String email) {

    public Customer {
        Objects.requireNonNull(customerId, "customerId");
    }
}
