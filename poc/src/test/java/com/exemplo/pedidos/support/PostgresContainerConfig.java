package com.exemplo.pedidos.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 16 único para todos os testes de integração, inclusive contextos Spring com
 * propriedades diferentes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
