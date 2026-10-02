package com.exemplo.pedidos.consumidor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor atual da API de Pedidos (frontend web, backoffice e ERP), escrito <strong>somente</strong>
 * contra {@code docs/openapi/baseline/orders-v1.0.0.yaml}.
 *
 * <p>Não envia {@code Idempotency-Key} nem conhece qualquer campo, header ou status posterior à
 * 1.0.0. A leitura é estrita (falha com campo desconhecido) para detectar qualquer mudança no
 * formato de resposta. <strong>Esta classe não pode ser alterada para acompanhar a evolução do
 * serviço</strong>: se um teste com ela falhar, o serviço quebrou um consumidor atual (ADR-007).
 */
public final class ConsumidorV1 {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;
    private final String token;

    public ConsumidorV1(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /** {@code POST /orders} (createOrderV1 da 1.0.0). */
    public Resposta criarPedido(String customerId, List<ItemSolicitado> itens) {
        String corpo = JSON.writeValueAsString(new CriarPedido(customerId, itens));
        return enviar(HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)));
    }

    /** {@code GET /orders/{id}} (getOrderV1 da 1.0.0). */
    public Resposta consultarPedido(long id) {
        return enviar(HttpRequest.newBuilder(URI.create(baseUrl + "/orders/" + id)).GET());
    }

    private Resposta enviar(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(
                    request.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new Resposta(response.statusCode(), response.headers().map(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record ItemSolicitado(String sku, int quantity) {
    }

    record CriarPedido(String customerId, List<ItemSolicitado> items) {
    }

    /** {@code Order} da 1.0.0. */
    public record Pedido(long id, String customerId, String status, BigDecimal total, List<ItemPedido> items,
            String createdAt) {
    }

    /** {@code OrderItem} da 1.0.0; {@code description} é opcional. */
    public record ItemPedido(String sku, int quantity, BigDecimal price, String description) {
    }

    /** {@code Error} da 1.0.0. */
    public record Erro(String message) {
    }

    public record Resposta(int status, Map<String, List<String>> headers, String corpo) {

        public Pedido pedido() {
            return JSON.readValue(corpo, Pedido.class);
        }

        public Erro erro() {
            return JSON.readValue(corpo, Erro.class);
        }
    }
}
