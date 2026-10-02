package com.exemplo.pedidos.application;

import java.util.UUID;

/**
 * Resultado da criação: corpo da resposta {@code 201} e indicação de que é a repetição de uma
 * criação anterior com a mesma chave ({@code Idempotent-Replayed}).
 */
public record CreateOrderResult(UUID orderId, int status, String body, boolean replayed) {
}
