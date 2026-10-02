package com.exemplo.pedidos.application;

import com.exemplo.pedidos.domain.Channel;
import com.exemplo.pedidos.domain.Customer;
import com.exemplo.pedidos.domain.Money;
import com.exemplo.pedidos.domain.OrderLine;
import java.util.List;

/**
 * Pedido de criação já validado pelo adaptador de entrada. {@code expectedTotal} e
 * {@code externalReference} são opcionais.
 */
public record CreateOrderCommand(Caller caller, Channel channel, Customer customer, List<OrderLine> lines,
        Money expectedTotal, String externalReference) {

    public CreateOrderCommand {
        lines = List.copyOf(lines);
    }
}
