package com.exemplo.pedidos.adapters.out.persistence;

import static com.exemplo.pedidos.adapters.out.persistence.JdbcOrderRepository.instant;
import static com.exemplo.pedidos.adapters.out.persistence.JdbcOrderRepository.utc;

import com.exemplo.pedidos.application.IdempotencyKeyTakenException;
import com.exemplo.pedidos.application.IdempotencyRecord;
import com.exemplo.pedidos.application.IdempotencyRepository;
import com.exemplo.pedidos.application.LockTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyRepository implements IdempotencyRepository {

    static final String KEY_CONSTRAINT = "idempotency_record_pk";

    private final JdbcClient jdbc;

    JdbcIdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findActive(String callerId, String key, Instant now) {
        return jdbc.sql("""
                        SELECT caller_id, idem_key, request_hash, order_id, response_status, response_body::text AS body,
                               created_at, expires_at
                        FROM idempotency_record
                        WHERE caller_id = :callerId AND idem_key = :key AND expires_at > :now""")
                .param("callerId", callerId)
                .param("key", key)
                .param("now", utc(now))
                .query((rs, n) -> new IdempotencyRecord(
                        rs.getString("caller_id"),
                        rs.getString("idem_key"),
                        rs.getString("request_hash"),
                        rs.getObject("order_id", UUID.class),
                        rs.getInt("response_status"),
                        rs.getString("body"),
                        instant(rs, "created_at"),
                        instant(rs, "expires_at")))
                .optional();
    }

    @Override
    public void limitLockWait(Duration timeout) {
        // SET não aceita parâmetros; o valor vem da configuração, nunca da requisição.
        jdbc.sql("SET LOCAL lock_timeout = '" + timeout.toMillis() + "ms'").update();
    }

    @Override
    public void deleteExpired(String callerId, String key, Instant now) {
        jdbc.sql("DELETE FROM idempotency_record WHERE caller_id = :callerId AND idem_key = :key AND expires_at <= :now")
                .param("callerId", callerId)
                .param("key", key)
                .param("now", utc(now))
                .update();
    }

    @Override
    public void insert(IdempotencyRecord record) {
        try {
            jdbc.sql("""
                            INSERT INTO idempotency_record (caller_id, idem_key, request_hash, order_id, response_status,
                                response_body, created_at, expires_at)
                            VALUES (:callerId, :key, :requestHash, :orderId, :status, CAST(:body AS jsonb),
                                :createdAt, :expiresAt)""")
                    .param("callerId", record.callerId())
                    .param("key", record.key())
                    .param("requestHash", record.requestHash())
                    .param("orderId", record.orderId())
                    .param("status", record.responseStatus())
                    .param("body", record.responseBody())
                    .param("createdAt", utc(record.createdAt()))
                    .param("expiresAt", utc(record.expiresAt()))
                    .update();
        } catch (DataAccessException e) {
            if (PostgresErrors.violates(e, KEY_CONSTRAINT)) {
                throw new IdempotencyKeyTakenException(e);
            }
            if (PostgresErrors.hasState(e, PostgresErrors.LOCK_NOT_AVAILABLE)) {
                throw new LockTimeoutException(e);
            }
            throw e;
        }
    }
}
