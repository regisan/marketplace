package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.CatalogItemView;
import com.exemplo.pedidos.domain.Order;
import com.exemplo.pedidos.domain.OrderLine;
import com.exemplo.pedidos.domain.UuidV7;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Criação de pedido: precificação pelo read model, snapshot e gravação em uma transação curta. */
@Service
public class CreateOrderUseCase {

    private final OrderRepository orders;
    private final CatalogItemViewRepository catalog;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final BigDecimal priceTolerance;

    public CreateOrderUseCase(OrderRepository orders, CatalogItemViewRepository catalog, TransactionTemplate tx,
            Clock clock, @Value("${pedidos.preco.tolerancia:0.00}") BigDecimal priceTolerance) {
        this.orders = orders;
        this.catalog = catalog;
        this.tx = tx;
        this.clock = clock;
        this.priceTolerance = priceTolerance;
    }

    public CreateOrderResult create(CreateOrderCommand command, ResponseRenderer renderer) {
        Order order = price(command);
        String body = renderer.render(order);
        try {
            tx.executeWithoutResult(status -> orders.insert(order));
        } catch (ExternalReferenceTakenException e) {
            throw duplicateExternalReference(command);
        }
        return new CreateOrderResult(order.id(), 201, body, false);
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
                .orElseThrow(() -> new IllegalStateException("Violação de externalReference sem pedido existente"));
    }
}
