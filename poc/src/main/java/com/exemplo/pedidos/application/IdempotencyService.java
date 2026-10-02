package com.exemplo.pedidos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Regras do ADR-005 sobre o registro de idempotência: reserva, repetição e conflito. */
@Service
public class IdempotencyService {

    private final IdempotencyRepository records;
    private final Clock clock;
    private final Duration validity;
    private final Duration lockTimeout;

    public IdempotencyService(IdempotencyRepository records, Clock clock,
            @Value("${pedidos.idempotencia.validade:PT24H}") Duration validity,
            @Value("${pedidos.idempotencia.lock-timeout:2s}") Duration lockTimeout) {
        this.records = records;
        this.clock = clock;
        this.validity = validity;
        this.lockTimeout = lockTimeout;
    }

    /** Resposta já registrada para a chave, se houver: repetição ou recusa por conteúdo diferente. */
    public Optional<CreateOrderResult> replayIfRecorded(Caller caller, IdempotencyRequest request) {
        return records.findActive(caller.callerId(), request.key(), clock.instant())
                .map(existing -> replayOrReject(existing, request));
    }

    /**
     * Reserva a chave na transação corrente, antes de qualquer outra escrita, gravando a resposta
     * já serializada. Deve ser chamada dentro da mesma transação do pedido e do outbox.
     */
    public void reserve(Caller caller, IdempotencyRequest request, CreateOrderResult response, Instant now) {
        records.limitLockWait(lockTimeout);
        records.deleteExpired(caller.callerId(), request.key(), now);
        records.insert(new IdempotencyRecord(caller.callerId(), request.key(), request.requestHash(),
                response.orderId(), response.status(), response.body(), now, now.plus(validity)));
    }

    /**
     * Após uma violação de unicidade e o rollback, lê o registro vencedor em uma nova leitura.
     * Se ele não estiver mais disponível, o chamador deve tentar de novo.
     */
    public CreateOrderResult resolveConflict(Caller caller, IdempotencyRequest request) {
        return replayIfRecorded(caller, request).orElseThrow(RequestInProgressException::new);
    }

    private static CreateOrderResult replayOrReject(IdempotencyRecord existing, IdempotencyRequest request) {
        if (!existing.requestHash().equals(request.requestHash())) {
            throw new IdempotencyKeyReuseException();
        }
        return new CreateOrderResult(existing.orderId(), existing.responseStatus(), existing.responseBody(), true);
    }
}
