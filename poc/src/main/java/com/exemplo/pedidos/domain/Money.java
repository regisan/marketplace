package com.exemplo.pedidos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Valor monetário em decimal com moeda ISO 4217; nunca ponto flutuante (ADR-004). */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Currency.getInstance(currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    /** Diferença absoluta entre dois valores na mesma moeda. */
    public BigDecimal distanceTo(Money other) {
        requireSameCurrency(other);
        return amount.subtract(other.amount).abs();
    }

    /** Valor arredondado às casas decimais da moeda (por exemplo, 2 para BRL). */
    public BigDecimal scaledAmount() {
        return amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public boolean sameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!sameCurrencyAs(other)) {
            throw new IllegalArgumentException("Moedas diferentes: " + currency + " e " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m && amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }
}
