package com.exemplo.pedidos.adapters.in.web;

import java.util.List;

/** Requisição inválida (formato ou campos obrigatórios), com os erros por campo. */
public class InvalidRequestException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidRequestException(List<FieldError> errors) {
        super("Requisição inválida: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    public record FieldError(String field, String message) {
    }
}
