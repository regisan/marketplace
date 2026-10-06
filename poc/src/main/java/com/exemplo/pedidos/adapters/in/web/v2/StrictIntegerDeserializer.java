package com.exemplo.pedidos.adapters.in.web.v2;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lê um {@code type: integer} do contrato sem as coerções padrão do Jackson, que truncam {@code 1.5}
 * para {@code 1} e aceitam {@code "2"} em texto. Como no JSON Schema, número com parte fracionária
 * zero ({@code 2.0}) é inteiro. Qualquer outro valor vira {@code null}, que o {@link OrderMapperV2}
 * rejeita com {@code 400} e o nome do campo.
 */
class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonToken token = p.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            p.skipChildren();
            return null;
        }
        BigDecimal value = p.getDecimalValue();
        try {
            return value.stripTrailingZeros().scale() > 0 ? null : value.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
