package com.exemplo.pedidos.adapters.in.web.v1;

import com.exemplo.pedidos.adapters.in.web.RequestHasher;
import com.exemplo.pedidos.adapters.in.web.RequestValidator;
import com.exemplo.pedidos.application.Caller;
import com.exemplo.pedidos.application.CreateOrderResult;
import com.exemplo.pedidos.application.CreateOrderUseCase;
import com.exemplo.pedidos.application.GetOrderUseCase;
import com.exemplo.pedidos.application.IdempotencyRequest;
import com.exemplo.pedidos.application.OrderNotFoundException;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * API v1 (orders-v1.yaml 1.1.0), em {@code /orders} e no alias {@code /v1/orders} (ADR-007). Sem
 * {@code Idempotency-Key}, o comportamento é o da 1.0.0 e nenhum registro de idempotência é criado.
 */
@RestController
@RequestMapping({"/orders", "/v1/orders"})
class OrderControllerV1 {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    static final String OPERATION_ID = "createOrderV1";

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final RequestHasher hasher;
    private final JsonMapper json;

    OrderControllerV1(CreateOrderUseCase createOrder, GetOrderUseCase getOrder, RequestHasher hasher,
            JsonMapper json) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.hasher = hasher;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<String> create(Caller caller,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CreateOrderRequestV1 request) {
        if (idempotencyKey != null) {
            new RequestValidator().requireText(idempotencyKey, IDEMPOTENCY_KEY, 64).throwIfInvalid();
        }
        var command = OrderMapperV1.toCommand(caller, request);
        Optional<IdempotencyRequest> idempotency = Optional.ofNullable(idempotencyKey)
                .map(key -> new IdempotencyRequest(key, hasher.hash("POST", OPERATION_ID, request)));

        CreateOrderResult result = createOrder.create(command, idempotency,
                order -> json.writeValueAsString(OrderResponseV1.from(order)));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.body());
    }

    /** Consulta pelo {@code legacyId}; pedidos criados pela v2 também aparecem aqui (QA-COM-03). */
    @GetMapping("/{id}")
    ResponseEntity<OrderResponseV1> get(Caller caller, @PathVariable String id) {
        return ResponseEntity.ok(OrderResponseV1.from(getOrder.byLegacyId(caller, parse(id))));
    }

    private static long parse(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new OrderNotFoundException();
        }
    }
}
