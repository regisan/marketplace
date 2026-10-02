package com.exemplo.pedidos.support;

import java.util.UUID;

/** Corpos de requisição usados nos testes. Preço sintético do SKU-n: 10,00 + (n mod 50) × 2,50. */
public final class Requests {

    public static final String TOKEN_ANA = "token-cliente-ana";
    public static final String TOKEN_BRUNO = "token-cliente-bruno";
    public static final String TOKEN_ACME = "token-parceiro-acme";
    public static final String TOKEN_ERP = "token-erp";

    private Requests() {
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    public static String newCustomerId() {
        return "c-" + UUID.randomUUID();
    }

    /** SKU-0001 × 2 (12,50) e SKU-0042 × 1 (115,00): total 140,00 BRL. */
    public static String v2(String customerId) {
        return v2(customerId, null, null);
    }

    public static String v2(String customerId, String expectedTotal, String externalReference) {
        return """
                {"customer":{"customerId":"%s","name":"Cliente Sintético","email":"cliente@exemplo.com"},
                 "items":[{"sku":"SKU-0001","quantity":2},{"sku":"SKU-0042","quantity":1}]%s%s}"""
                .formatted(customerId,
                        expectedTotal == null ? "" : ",\"expectedTotal\":{\"amount\":\"%s\",\"currency\":\"BRL\"}".formatted(expectedTotal),
                        externalReference == null ? "" : ",\"externalReference\":\"%s\"".formatted(externalReference));
    }

    public static String v2WithSku(String customerId, String sku) {
        return """
                {"customer":{"customerId":"%s","name":"Cliente Sintético","email":"cliente@exemplo.com"},
                 "items":[{"sku":"%s","quantity":1}]}""".formatted(customerId, sku);
    }
}
