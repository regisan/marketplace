package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.Order;

/**
 * Serializa a resposta de criação no formato da versão da API. A resposta é gerada antes do
 * commit para ser armazenada no registro de idempotência e devolvida igual nas repetições.
 */
@FunctionalInterface
public interface ResponseRenderer {

    String render(Order order);
}
