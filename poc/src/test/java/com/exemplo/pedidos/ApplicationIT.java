package com.exemplo.pedidos;

import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ApplicationIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("Contexto sobe com PostgreSQL 16 e migrações Flyway aplicadas")
    void contextStartsWithMigrations() {
        String version = jdbc.sql("SHOW server_version").query(String.class).single();
        Integer applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success")
                .query(Integer.class).single();

        assertThat(version).startsWith("16.");
        assertThat(applied).isPositive();
    }
}
