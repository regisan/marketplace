package com.exemplo.pedidos.domain;

import java.util.List;

/** Um ou mais SKUs não existem ou não estão à venda no país. */
public class UnknownSkuException extends RuntimeException {

    private final List<String> skus;

    public UnknownSkuException(List<String> skus) {
        super("SKUs desconhecidos ou não vendáveis: " + skus);
        this.skus = List.copyOf(skus);
    }

    public List<String> skus() {
        return skus;
    }
}
