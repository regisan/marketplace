package com.exemplo.pedidos.application;

import java.time.Instant;
import java.util.UUID;

/** Registro de idempotência: a resposta original de uma criação, válida até {@code expiresAt}. */
public record IdempotencyRecord(String callerId, String key, String requestHash, UUID orderId,
        int responseStatus, String responseBody, Instant createdAt, Instant expiresAt) {
}
