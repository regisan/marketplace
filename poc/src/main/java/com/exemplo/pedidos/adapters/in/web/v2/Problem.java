package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.adapters.in.web.InvalidRequestException;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * RFC 9457 Problem Details com as extensões de orders-v2.yaml. Os erros sem causa específica no
 * contrato ({@code 400}, {@code 404}) usam {@code type: about:blank} (RFC 9457, seção 4.2.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record Problem(
        String type,
        String title,
        int status,
        String detail,
        Boolean retryable,
        UUID existingOrderId,
        List<CurrentItem> currentItems,
        List<InvalidRequestException.FieldError> errors) {

    static final String BASE = "https://api.exemplo.com/problems/";
    static final String ABOUT_BLANK = "about:blank";

    static Problem of(String type, String title, int status) {
        return new Problem(type, title, status, null, null, null, null, null);
    }

    Problem withDetail(String value) {
        return new Problem(type, title, status, value, retryable, existingOrderId, currentItems, errors);
    }

    Problem withRetryable(boolean value) {
        return new Problem(type, title, status, detail, value, existingOrderId, currentItems, errors);
    }

    Problem withExistingOrderId(UUID value) {
        return new Problem(type, title, status, detail, retryable, value, currentItems, errors);
    }

    Problem withCurrentItems(List<CurrentItem> value) {
        return new Problem(type, title, status, detail, retryable, existingOrderId, value, errors);
    }

    Problem withErrors(List<InvalidRequestException.FieldError> value) {
        return new Problem(type, title, status, detail, retryable, existingOrderId, currentItems, value);
    }

    record CurrentItem(String sku, MoneyV2 unitPrice) {
    }
}
