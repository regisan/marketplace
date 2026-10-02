package com.exemplo.pedidos.adapters.out.persistence;

import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/** Leitura do SQLState e da constraint violada a partir da cadeia de causas de uma exceção. */
final class PostgresErrors {

    static final String UNIQUE_VIOLATION = "23505";
    static final String LOCK_NOT_AVAILABLE = "55P03";

    private PostgresErrors() {
    }

    static boolean hasState(Throwable error, String sqlState) {
        return psql(error).map(e -> sqlState.equals(e.getSQLState())).orElse(false);
    }

    static boolean violates(Throwable error, String constraint) {
        return psql(error)
                .filter(e -> UNIQUE_VIOLATION.equals(e.getSQLState()))
                .map(PSQLException::getServerErrorMessage)
                .map(ServerErrorMessage::getConstraint)
                .filter(constraint::equals)
                .isPresent();
    }

    private static Optional<PSQLException> psql(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PSQLException e) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
