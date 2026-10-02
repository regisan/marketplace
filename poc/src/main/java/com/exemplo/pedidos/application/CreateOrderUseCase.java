package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.CatalogItemView;
import com.exemplo.pedidos.domain.Order;
import com.exemplo.pedidos.domain.OrderCreated;
import com.exemplo.pedidos.domain.OrderLine;
import com.exemplo.pedidos.domain.UuidV7;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Criação de pedido (ADR-005). Precificação, snapshot e serialização da resposta acontecem antes da
 * transação; dentro dela só há escritas: registro de idempotência, pedido com itens e evento.
 */
@Service
public class CreateOrderUseCase {

    private final OrderRepository orders;
    private final CatalogItemViewRepository catalog;
    private final OutboxRepository outbox;
    private final IdempotencyService idempotency;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final BigDecimal priceTolerance;

    public CreateOrderUseCase(OrderRepository orders, CatalogItemViewRepository catalog, OutboxRepository outbox,
            IdempotencyService idempotency, TransactionTemplate tx, Clock clock,
            @Value("${pedidos.preco.tolerancia:0.00}") BigDecimal priceTolerance) {
        this.orders = orders;
        this.catalog = catalog;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.tx = tx;
        this.clock = clock;
        this.priceTolerance = priceTolerance;
    }

    /**
     * @param idempotencyRequest ausente apenas na v1 sem header, que mantém o comportamento da 1.0.0
     */
    public CreateOrderResult create(CreateOrderCommand command, Optional<IdempotencyRequest> idempotencyRequest,
            ResponseRenderer renderer) {
        Caller caller = command.caller();
        if (idempotencyRequest.isPresent()) {
            // Caminho rápido: repetição de uma criação já confirmada responde igual, mesmo que o catálogo tenha mudado.
            Optional<CreateOrderResult> recorded = idempotency.replayIfRecorded(caller, idempotencyRequest.get());
            if (recorded.isPresent()) {
                return recorded.get();
            }
        }

        Order order = price(command);
        OrderCreated event = OrderCreated.of(order, UuidV7.generate(clock));
        CreateOrderResult created = new CreateOrderResult(order.id(), 201, renderer.render(order), false);
        try {
            tx.executeWithoutResult(status -> {
                idempotencyRequest.ifPresent(request ->
                        idempotency.reserve(caller, request, created, order.createdAt()));
                orders.insert(order);
                outbox.append(event);
            });
        } catch (IdempotencyKeyTakenException e) {
            return idempotency.resolveConflict(caller, idempotencyRequest.orElseThrow());
        } catch (ExternalReferenceTakenException e) {
            throw duplicateExternalReference(command);
        } catch (LockTimeoutException e) {
            throw new RequestInProgressException();
        }
        return created;
    }

    /** Precifica e monta o pedido fora da transação: nenhuma leitura de rede dentro dela. */
    private Order price(CreateOrderCommand command) {
        Caller caller = command.caller();
        Map<String, CatalogItemView> views = catalog.findBySkus(caller.country(),
                command.lines().stream().map(OrderLine::sku).distinct().toList());
        // Precisão de milissegundos: a resposta é serializada antes de gravar e precisa ser igual à lida depois.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        Order order = Order.create(new Order.NewOrder(
                UuidV7.generate(clock),
                orders.nextLegacyId(),
                caller.country(),
                caller.callerId(),
                caller.isPartner() ? caller.partnerId() : null,
                caller.isPartner() ? command.externalReference() : null,
                command.channel(),
                command.customer(),
                command.lines()), views, now);
        order.ensureExpectedTotal(command.expectedTotal(), priceTolerance, views);
        return order;
    }

    private DuplicateExternalReferenceException duplicateExternalReference(CreateOrderCommand command) {
        return orders.findIdByExternalReference(command.caller().partnerId(), command.externalReference())
                .map(DuplicateExternalReferenceException::new)
                .orElseThrow(RequestInProgressException::new);
    }
}
