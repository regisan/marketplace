package com.exemplo.pedidos.domain;

/** Item solicitado na criação: apenas SKU e quantidade. Preço nunca vem da requisição. */
public record OrderLine(String sku, int quantity) {
}
