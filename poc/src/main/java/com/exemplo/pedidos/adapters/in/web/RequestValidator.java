package com.exemplo.pedidos.adapters.in.web;

import java.util.ArrayList;
import java.util.List;

/** Acumula erros de validação de entrada e lança {@link InvalidRequestException} se houver algum. */
public final class RequestValidator {

    private final List<InvalidRequestException.FieldError> errors = new ArrayList<>();

    public RequestValidator require(boolean condition, String field, String message) {
        if (!condition) {
            errors.add(new InvalidRequestException.FieldError(field, message));
        }
        return this;
    }

    public RequestValidator requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return require(false, field, "obrigatório");
        }
        return require(value.length() <= maxLength, field, "no máximo " + maxLength + " caracteres");
    }

    public RequestValidator optionalText(String value, String field, int maxLength) {
        return value == null ? this : require(value.length() <= maxLength, field, "no máximo " + maxLength + " caracteres");
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
    }
}
