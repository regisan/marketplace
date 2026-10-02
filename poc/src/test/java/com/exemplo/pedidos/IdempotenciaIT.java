package com.exemplo.pedidos;

import static com.exemplo.pedidos.support.Requests.TOKEN_ACME;
import static com.exemplo.pedidos.support.Requests.TOKEN_ANA;
import static com.exemplo.pedidos.support.Requests.TOKEN_BRUNO;
import static com.exemplo.pedidos.support.Requests.newCustomerId;
import static com.exemplo.pedidos.support.Requests.newKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.pedidos.support.ApiClient;
import com.exemplo.pedidos.support.ContractValidator;
import com.exemplo.pedidos.support.IntegrationTest;
import com.exemplo.pedidos.support.Requests;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-005 e FF-14: chamadas repetidas com a mesma {@code Idempotency-Key} não criam pedidos
 * duplicados, em sequência ou simultâneas.
 *
 * <p>O {@code lock_timeout} sobe de 2 s para 5 s neste teste para dar folga ao portão do QA-INT-02
 * em máquinas lentas; o mecanismo é o mesmo.
 */
@IntegrationTest
@TestPropertySource(properties = "pedidos.idempotencia.lock-timeout=5s")
class IdempotenciaIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int CONCURRENT_REQUESTS = 20;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    ApiClient api;
    ExecutorService executor;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("QA-INT-01: mesma chave e corpo em sequência criam 1 pedido; a repetição devolve a resposta original")
    void sequentialRepeat() {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);

        ApiClient.Response first = api.post("/v2/orders", TOKEN_ANA, key, body);
        ApiClient.Response second = api.post("/v2/orders", TOKEN_ANA, key, body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json(second)).isEqualTo(json(first));
        assertThat(second.header("Location")).isEqualTo(first.header("Location"));
        ContractValidator.V2.assertValid("POST", "/orders", second.status(), second.headers(), second.body());
        assertThat(ordersOf(customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-INT-02: 20 requisições simultâneas, comprovadamente em disputa no banco, criam exatamente 1 pedido")
    void concurrentRequestsGatedAtDatabase() throws Exception {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);
        warmUp();

        List<ApiClient.Response> responses;
        try (Connection gate = holdKey("cliente-ana", key)) {
            List<CompletableFuture<ApiClient.Response>> inFlight =
                    fire(() -> api.post("/v2/orders", TOKEN_ANA, key, body));
            awaitRequestsWaitingOnKey(CONCURRENT_REQUESTS);
            gate.rollback();
            responses = join(inFlight);
        }

        assertExactlyOneOrder(customerId, key, responses);
        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(CONCURRENT_REQUESTS);
    }

    @Test
    @DisplayName("QA-INT-02: com a chave presa além do lock_timeout, as 20 requisições recebem 409 request-in-progress")
    void concurrentRequestsTimeOut() throws Exception {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);
        warmUp();

        List<ApiClient.Response> responses;
        try (Connection gate = holdKey("cliente-ana", key)) {
            List<CompletableFuture<ApiClient.Response>> inFlight =
                    fire(() -> api.post("/v2/orders", TOKEN_ANA, key, body));
            awaitRequestsWaitingOnKey(CONCURRENT_REQUESTS);
            responses = join(inFlight);
            gate.rollback();
        }

        assertThat(responses).hasSize(CONCURRENT_REQUESTS).allSatisfy(response -> {
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.header("Retry-After")).isEqualTo("1");
            assertThat(json(response).path("type").asString())
                    .isEqualTo("https://api.exemplo.com/problems/request-in-progress");
            ContractValidator.V2.assertValid("POST", "/orders", response.status(), response.headers(), response.body());
        });
        assertThat(ordersOf(customerId)).isZero();
    }

    @RepeatedTest(5)
    @DisplayName("QA-INT-02: 20 requisições simultâneas sem portão mantêm as mesmas garantias")
    void concurrentRequestsNatural() {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);

        List<ApiClient.Response> responses = join(fire(() -> api.post("/v2/orders", TOKEN_ANA, key, body)));

        assertExactlyOneOrder(customerId, key, responses);
    }

    @Test
    @DisplayName("QA-INT-03: mesma chave com corpo diferente retorna 422 e não cria pedido")
    void sameKeyDifferentBody() {
        String customerId = newCustomerId();
        String key = newKey();
        api.post("/v2/orders", TOKEN_ANA, key, Requests.v2(customerId));

        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, key, Requests.v2WithSku(customerId, "SKU-0003"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(json(response).path("type").asString())
                .isEqualTo("https://api.exemplo.com/problems/idempotency-key-reuse");
        ContractValidator.V2.assertValid("POST", "/orders", response.status(), response.headers(), response.body());
        assertThat(ordersOf(customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-005: campos ignorados (status, unitPrice) não mudam o conteúdo da requisição")
    void ignoredFieldsDoNotChangeHash() {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);
        api.post("/v2/orders", TOKEN_ANA, key, body);

        ApiClient.Response repeated = api.post("/v2/orders", TOKEN_ANA, key,
                body.replaceFirst("\\{", "{\"status\":\"SHIPPED\",\"unitPrice\":\"0.01\","));

        assertThat(repeated.status()).isEqualTo(201);
        assertThat(repeated.header("Idempotent-Replayed")).isEqualTo("true");
    }

    @Test
    @DisplayName("ADR-005: mesma chave usada por chamadores diferentes cria 2 pedidos")
    void sameKeyDifferentCallers() {
        String customerId = newCustomerId();
        String key = newKey();

        ApiClient.Response ana = api.post("/v2/orders", TOKEN_ANA, key, Requests.v2(customerId));
        ApiClient.Response bruno = api.post("/v2/orders", TOKEN_BRUNO, key, Requests.v2(customerId));

        assertThat(ana.status()).isEqualTo(201);
        assertThat(bruno.status()).isEqualTo(201);
        assertThat(bruno.header("Idempotent-Replayed")).isNull();
        assertThat(json(bruno).path("id")).isNotEqualTo(json(ana).path("id"));
        assertThat(ordersOf(customerId)).isEqualTo(2);
    }

    @Test
    @DisplayName("ADR-005: v2 sem Idempotency-Key retorna 400 descritivo")
    void v2WithoutKey() {
        String customerId = newCustomerId();

        ApiClient.Response response = api.post("/v2/orders", TOKEN_ANA, null, Requests.v2(customerId));

        assertThat(response.status()).isEqualTo(400);
        assertThat(json(response).path("errors").get(0).path("field").asString()).isEqualTo("Idempotency-Key");
        ContractValidator.V2.assertValid("POST", "/orders", response.status(), response.headers(), response.body());
        assertThat(ordersOf(customerId)).isZero();
    }

    @Test
    @DisplayName("ADR-005: chave vencida (mais de 24 h) cria um novo pedido")
    void expiredKey() {
        String customerId = newCustomerId();
        String key = newKey();
        String body = Requests.v2(customerId);
        ApiClient.Response first = api.post("/v2/orders", TOKEN_ANA, key, body);
        jdbc.sql("UPDATE idempotency_record SET expires_at = now() - interval '1 second' WHERE idem_key = ?")
                .param(key).update();

        ApiClient.Response second = api.post("/v2/orders", TOKEN_ANA, key, body);

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isNull();
        assertThat(json(second).path("id")).isNotEqualTo(json(first).path("id"));
        assertThat(ordersOf(customerId)).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM idempotency_record WHERE idem_key = ?").param(key)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("QA-INT-06: parceiro com chaves diferentes e mesma externalReference recebe 409 com existingOrderId")
    void partnerDuplicateExternalReference() {
        String customerId = newCustomerId();
        String externalReference = "MKT-" + UUID.randomUUID();

        ApiClient.Response first = api.post("/v2/orders", TOKEN_ACME, newKey(),
                Requests.v2(customerId, null, externalReference));
        ApiClient.Response second = api.post("/v2/orders", TOKEN_ACME, newKey(),
                Requests.v2(customerId, null, externalReference));

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(409);
        JsonNode problem = json(second);
        assertThat(problem.path("type").asString())
                .isEqualTo("https://api.exemplo.com/problems/duplicate-external-reference");
        assertThat(problem.path("existingOrderId")).isEqualTo(json(first).path("id"));
        ContractValidator.V2.assertValid("POST", "/orders", second.status(), second.headers(), second.body());
        assertThat(ordersOf(customerId)).isEqualTo(1);
    }

    /**
     * Critério do QA-INT-02: exatamente 1 pedido e 1 registro; todas as respostas são a resposta
     * original (uma única sem {@code Idempotent-Replayed}) ou {@code 409 request-in-progress}.
     */
    private void assertExactlyOneOrder(String customerId, String key, List<ApiClient.Response> responses) {
        assertThat(ordersOf(customerId)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM idempotency_record WHERE idem_key = ?").param(key)
                .query(Integer.class).single()).isEqualTo(1);

        List<ApiClient.Response> created = responses.stream().filter(r -> r.status() == 201).toList();
        List<ApiClient.Response> others = responses.stream().filter(r -> r.status() != 201).toList();
        assertThat(created).isNotEmpty();
        assertThat(created).filteredOn(r -> r.header("Idempotent-Replayed") == null).hasSize(1);
        JsonNode original = json(created.getFirst());
        assertThat(created).allSatisfy(r -> assertThat(json(r)).isEqualTo(original));
        assertThat(others).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(409);
            assertThat(r.header("Retry-After")).isEqualTo("1");
        });
    }

    /** Abre uma transação que insere e segura a linha (chamador, chave) sem confirmar. */
    private Connection holdKey(String callerId, String key) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO idempotency_record (caller_id, idem_key, request_hash, order_id, response_status,
                    response_body, created_at, expires_at)
                VALUES (?, ?, repeat('0', 64), gen_random_uuid(), 201, '{}', now(), now() + interval '1 day')""")) {
            insert.setString(1, callerId);
            insert.setString(2, key);
            insert.executeUpdate();
        }
        return connection;
    }

    /**
     * Espera até que {@code expected} transações estejam bloqueadas no INSERT do registro de
     * idempotência, aguardando a mesma linha única. É a prova de que as requisições chegaram ao
     * mesmo tempo ao banco, e não apenas ao servidor.
     */
    private void awaitRequestsWaitingOnKey(int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(LOCK_TIMEOUT.minusSeconds(1));
        int waiting = 0;
        while (Instant.now().isBefore(deadline)) {
            waiting = jdbc.sql("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND wait_event_type = 'Lock' AND wait_event = 'transactionid'
                              AND query LIKE 'INSERT INTO idempotency_record%'""")
                    .query(Integer.class).single();
            if (waiting == expected) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Esperadas %d transações bloqueadas na chave; observadas %d".formatted(expected, waiting));
    }

    /** Dispara as requisições ao mesmo tempo a partir de uma barreira. */
    private List<CompletableFuture<ApiClient.Response>> fire(Supplier<ApiClient.Response> call) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<ApiClient.Response>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return call.get();
            }, executor));
        }
        start.countDown();
        return futures;
    }

    private static List<ApiClient.Response> join(List<CompletableFuture<ApiClient.Response>> futures) {
        return futures.stream().map(f -> {
            try {
                return f.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    /** Aquece threads do Tomcat, conexões HTTP e caminho de código antes de medir simultaneidade. */
    private void warmUp() {
        join(fire(() -> api.get("/v2/orders/" + UUID.randomUUID(), TOKEN_ANA)));
    }

    private int ordersOf(String customerId) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE customer_id = ?").param(customerId)
                .query(Integer.class).single();
    }

    private static JsonNode json(ApiClient.Response response) {
        return JSON.readTree(response.body());
    }
}
