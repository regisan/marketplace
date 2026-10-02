package com.exemplo.pedidos.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Porta de persistência dos registros de idempotência (ADR-005). */
public interface IdempotencyRepository {

    /** Registro ainda válido em {@code now}; registros vencidos são tratados como inexistentes. */
    Optional<IdempotencyRecord> findActive(String callerId, String key, Instant now);

    /** Limita a espera por locks na transação corrente ({@code SET LOCAL lock_timeout}). */
    void limitLockWait(Duration timeout);

    /** Remove, na transação corrente, o registro vencido desta chave, liberando-a para reúso. */
    void deleteExpired(String callerId, String key, Instant now);

    /**
     * Insere o registro na transação corrente. É o ponto de disputa entre requisições simultâneas
     * com a mesma chave: a segunda espera a primeira terminar.
     *
     * @throws IdempotencyKeyTakenException se já houver registro para (chamador, chave)
     * @throws LockTimeoutException se a espera passar do limite
     */
    void insert(IdempotencyRecord record);
}
