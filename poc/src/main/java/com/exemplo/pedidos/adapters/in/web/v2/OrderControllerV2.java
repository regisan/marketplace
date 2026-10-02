package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.adapters.in.web.RequestValidator;
import com.exemplo.pedidos.application.Caller;
import com.exemplo.pedidos.application.CreateOrderCommand;
import com.exemplo.pedidos.application.CreateOrderResult;
import com.exemplo.pedidos.application.CreateOrderUseCase;
import com.exemplo.pedidos.application.GetOrderUseCase;
import com.exemplo.pedidos.application.OrderNotFoundException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** API v2 de Pedidos (orders-v2.yaml). */
@RestController
@RequestMapping("/v2/orders")
class OrderControllerV2 {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final JsonMapper json;

    OrderControllerV2(CreateOrderUseCase createOrder, GetOrderUseCase getOrder, JsonMapper json) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<String> create(Caller caller,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CreateOrderRequestV2 request) {
        new RequestValidator()
                .requireText(idempotencyKey, IDEMPOTENCY_KEY, 64)
                .throwIfInvalid();
        CreateOrderCommand command = OrderMapperV2.toCommand(caller, request);

        CreateOrderResult result = createOrder.create(command,
                order -> json.writeValueAsString(OrderResponseV2.from(order)));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .location(location(result.orderId()))
                .contentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.body());
    }

    @GetMapping("/{orderId}")
    ResponseEntity<OrderResponseV2> get(Caller caller, @PathVariable String orderId) {
        return ResponseEntity.ok(OrderResponseV2.from(getOrder.byId(caller, parse(orderId))));
    }

    /** O contrato só declara {@code 404} para a consulta: identificador malformado também é "não encontrado". */
    private static UUID parse(String orderId) {
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new OrderNotFoundException();
        }
    }

    private static URI location(UUID orderId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v2/orders/{id}")
                .buildAndExpand(orderId)
                .toUri();
    }
}
