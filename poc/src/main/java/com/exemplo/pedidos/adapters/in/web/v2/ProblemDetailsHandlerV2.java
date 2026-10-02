package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.adapters.in.web.InvalidRequestException;
import com.exemplo.pedidos.application.DuplicateExternalReferenceException;
import com.exemplo.pedidos.application.OrderNotFoundException;
import com.exemplo.pedidos.domain.PriceChangedException;
import com.exemplo.pedidos.domain.UnknownSkuException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros da v2 em Problem Details (RFC 9457), com os valores de {@code type} do contrato. */
@RestControllerAdvice(assignableTypes = OrderControllerV2.class)
class ProblemDetailsHandlerV2 {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandlerV2.class);

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Problem> invalid(InvalidRequestException e) {
        return respond(Problem.of(Problem.ABOUT_BLANK, "Bad Request", 400)
                .withDetail("Requisição inválida.")
                .withRetryable(false)
                .withErrors(e.errors()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<Problem> unreadable(Exception e) {
        return respond(Problem.of(Problem.ABOUT_BLANK, "Bad Request", 400)
                .withDetail("Corpo ausente ou JSON malformado.")
                .withRetryable(false));
    }

    @ExceptionHandler(UnknownSkuException.class)
    ResponseEntity<Problem> unknownSku(UnknownSkuException e) {
        return respond(Problem.of(Problem.BASE + "unknown-sku", "Um ou mais SKUs não estão à venda", 422)
                .withRetryable(false)
                .withErrors(e.skus().stream()
                        .map(sku -> new InvalidRequestException.FieldError("items.sku", "SKU não vendável no país: " + sku))
                        .toList()));
    }

    @ExceptionHandler(PriceChangedException.class)
    ResponseEntity<Problem> priceChanged(PriceChangedException e) {
        List<Problem.CurrentItem> current = e.currentItems().stream()
                .map(item -> new Problem.CurrentItem(item.sku(), MoneyV2.from(item.price())))
                .toList();
        return respond(Problem.of(Problem.BASE + "price-changed", "O preço de um ou mais itens mudou", 409)
                .withRetryable(true)
                .withCurrentItems(current));
    }

    @ExceptionHandler(DuplicateExternalReferenceException.class)
    ResponseEntity<Problem> duplicateExternalReference(DuplicateExternalReferenceException e) {
        return respond(Problem.of(Problem.BASE + "duplicate-external-reference",
                        "Já existe um pedido com esta referência externa", 409)
                .withRetryable(false)
                .withExistingOrderId(e.existingOrderId()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Problem> notFound(OrderNotFoundException e) {
        return respond(Problem.of(Problem.ABOUT_BLANK, "Not Found", 404).withDetail("Pedido não encontrado."));
    }

    /** Mensagem genérica; detalhes apenas no log interno (API-06). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception e) {
        log.error("Erro inesperado na API v2", e);
        return respond(Problem.of(Problem.ABOUT_BLANK, "Internal Server Error", 500).withRetryable(true));
    }

    static ResponseEntity<Problem> respond(Problem problem) {
        return ResponseEntity.status(HttpStatus.valueOf(problem.status()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
