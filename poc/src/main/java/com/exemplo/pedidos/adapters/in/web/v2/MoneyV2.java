package com.exemplo.pedidos.adapters.in.web.v2;

import com.exemplo.pedidos.domain.Money;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Valor decimal em texto, para evitar perda de precisão. */
@JsonIgnoreProperties(ignoreUnknown = true)
record MoneyV2(String amount, String currency) {

    static MoneyV2 from(Money money) {
        return new MoneyV2(money.scaledAmount().toPlainString(), money.currency());
    }
}
