package com.exemplo.pedidos.domain;

/** Status do pedido (mapa de domínios, seção 4). Enum extensível nos contratos v2 e nos eventos. */
public enum OrderStatus {
    CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}
