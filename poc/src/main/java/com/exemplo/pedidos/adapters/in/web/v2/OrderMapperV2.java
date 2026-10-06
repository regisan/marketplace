package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.adapters.in.web.RequestValidator;
import com.exemplo.pedidos.application.Caller;
import com.exemplo.pedidos.application.CreateOrderCommand;
import com.exemplo.pedidos.domain.Customer;
import com.exemplo.pedidos.domain.Money;
import com.exemplo.pedidos.domain.OrderLine;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

/** Valida a requisição v2 segundo orders-v2.yaml e a traduz para o comando do domínio. */
final class OrderMapperV2 {

    static final int MAX_ITEMS = 300;
    static final int MAX_QUANTITY = 999;

    private static final Pattern AMOUNT = Pattern.compile("^-?\\d{1,15}(\\.\\d{1,4})?$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private OrderMapperV2() {
    }

    static CreateOrderCommand toCommand(Caller caller, CreateOrderRequestV2 request) {
        RequestValidator v = new RequestValidator();
        v.require(request != null, "body", "obrigatório").throwIfInvalid();

        CustomerV2 customer = request.customer();
        if (v.require(customer != null, "customer", "obrigatório").hasErrors()) {
            customer = new CustomerV2(null, null, null);
        }
        v.requireText(customer.customerId(), "customer.customerId", 64)
                .requireText(customer.name(), "customer.name", 200)
                .requireText(customer.email(), "customer.email", 320);
        if (customer.email() != null) {
            v.require(EMAIL.matcher(customer.email()).matches(), "customer.email", "e-mail inválido");
        }

        List<CreateOrderRequestV2.Item> items = request.items() == null ? List.of() : request.items();
        v.require(!items.isEmpty(), "items", "ao menos um item")
                .require(items.size() <= MAX_ITEMS, "items", "no máximo " + MAX_ITEMS + " itens");
        for (int i = 0; i < items.size(); i++) {
            CreateOrderRequestV2.Item item = items.get(i);
            String field = "items[" + i + "]";
            if (v.require(item != null, field, "obrigatório").hasErrors() && item == null) {
                continue;
            }
            v.requireText(item.sku(), field + ".sku", 64)
                    .require(item.quantity() != null && item.quantity() >= 1 && item.quantity() <= MAX_QUANTITY,
                            field + ".quantity", "inteiro entre 1 e " + MAX_QUANTITY);
        }

        MoneyV2 expected = request.expectedTotal();
        if (expected != null) {
            v.require(expected.amount() != null && AMOUNT.matcher(expected.amount()).matches(),
                            "expectedTotal.amount", "decimal em texto, até 4 casas")
                    .require(isIsoCurrency(expected.currency()), "expectedTotal.currency", "código ISO 4217");
        }

        v.optionalText(request.externalReference(), "externalReference", 64);
        if (caller.isPartner()) {
            v.requireText(request.externalReference(), "externalReference", 64);
        }
        v.throwIfInvalid();

        return new CreateOrderCommand(
                caller,
                caller.channel(),
                new Customer(customer.customerId(), customer.name(), customer.email()),
                items.stream().map(item -> new OrderLine(item.sku(), item.quantity())).toList(),
                expected == null ? null : Money.of(expected.amount(), expected.currency()),
                request.externalReference());
    }

    /** O pattern do contrato aceita qualquer trio de letras; {@link Money} exige uma moeda ISO 4217 existente. */
    static boolean isIsoCurrency(String code) {
        if (code == null || !CURRENCY.matcher(code).matches()) {
            return false;
        }
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
