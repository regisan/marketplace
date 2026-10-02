package com.exemplo.pedidos.adapters.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code requestHash} do ADR-005: SHA-256 de método, operação e corpo JSON canonizado.
 *
 * <p>A operação é o {@code operationId} do contrato, e não o caminho literal: {@code /orders} e
 * {@code /v1/orders} são a mesma operação, enquanto v1 e v2 são operações diferentes. O corpo
 * canonizado é o DTO de entrada já desserializado (apenas campos conhecidos), com chaves
 * ordenadas e sem espaços; campos ignorados não alteram o hash.
 */
@Component
public class RequestHasher {

    private final JsonMapper json;

    public RequestHasher(JsonMapper json) {
        this.json = json;
    }

    public String hash(String method, String operationId, Object requestBody) {
        String canonical = method + " " + operationId + "\n" + canonicalize(json.valueToTree(requestBody));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    String canonicalize(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "null";
        }
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            StringBuilder out = new StringBuilder("{");
            sorted.forEach((name, value) -> {
                if (out.length() > 1) {
                    out.append(',');
                }
                out.append(json.writeValueAsString(name)).append(':').append(canonicalize(value));
            });
            return out.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder("[");
            for (JsonNode element : node) {
                if (out.length() > 1) {
                    out.append(',');
                }
                out.append(canonicalize(element));
            }
            return out.append(']').toString();
        }
        return json.writeValueAsString(node);
    }
}
