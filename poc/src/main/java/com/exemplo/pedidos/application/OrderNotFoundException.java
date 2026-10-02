package com.exemplo.pedidos.application;

/** Pedido inexistente ou de outro chamador; os dois casos são indistinguíveis (API-01, API-02). */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Pedido não encontrado");
    }
}
