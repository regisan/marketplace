package com.exemplo.pedidos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    @DisplayName("RFC 9562: versão 7, variante IETF e timestamp em milissegundos nos 48 bits iniciais")
    void layout() {
        Instant instant = Instant.parse("2026-10-02T14:03:11.218Z");
        UUID uuid = UuidV7.generate(Clock.fixed(instant, ZoneOffset.UTC));

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(instant.toEpochMilli());
    }

    @Test
    @DisplayName("Identificadores de instantes diferentes ordenam pelo tempo")
    void timeOrdered() {
        UUID earlier = UuidV7.generate(Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC));
        UUID later = UuidV7.generate(Clock.fixed(Instant.parse("2026-10-02T10:00:00.001Z"), ZoneOffset.UTC));

        assertThat(earlier.toString()).isLessThan(later.toString());
    }
}
