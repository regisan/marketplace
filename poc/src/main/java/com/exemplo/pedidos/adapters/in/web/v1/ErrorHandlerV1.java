package com.exemplo.pedidos.adapters.in.web.v1;

import com.exemplo.pedidos.adapters.in.web.InvalidRequestException;
import com.exemplo.pedidos.application.IdempotencyKeyReuseException;
import com.exemplo.pedidos.application.OrderNotFoundException;
import com.exemplo.pedidos.application.RequestInProgressException;
import com.exemplo.pedidos.domain.UnknownSkuException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros da v1 no formato legado {@code {"message": ...}}. */
@RestControllerAdvice(assignableTypes = OrderControllerV1.class)
class ErrorHandlerV1 {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerV1.class);

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ErrorV1> invalid(InvalidRequestException e) {
        String detail = e.errors().stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "Requisição inválida: " + detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorV1> unreadable(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, "Corpo ausente ou JSON malformado.");
    }

    /** A v1 não tem {@code 422} para SKU desconhecido: o contrato 1.0.0 só conhece {@code 400}. */
    @ExceptionHandler(UnknownSkuException.class)
    ResponseEntity<ErrorV1> unknownSku(UnknownSkuException e) {
        return respond(HttpStatus.BAD_REQUEST, "SKUs desconhecidos ou não vendáveis: " + String.join(", ", e.skus()));
    }

    @ExceptionHandler(RequestInProgressException.class)
    ResponseEntity<ErrorV1> requestInProgress(RequestInProgressException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RequestInProgressException.RETRY_AFTER_SECONDS))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorV1("Requisição com a mesma Idempotency-Key em processamento."));
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    ResponseEntity<ErrorV1> keyReuse(IdempotencyKeyReuseException e) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency-Key já usada com outro conteúdo.");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorV1> notFound(OrderNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorV1> unexpected(Exception e) {
        log.error("Erro inesperado na API v1", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno.");
    }

    private static ResponseEntity<ErrorV1> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ErrorV1(message));
    }
}
