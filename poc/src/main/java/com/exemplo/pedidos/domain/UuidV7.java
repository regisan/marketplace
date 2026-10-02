package com.exemplo.pedidos.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** Gerador de UUID versão 7 (RFC 9562): 48 bits de timestamp em milissegundos e 74 aleatórios. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(Clock.systemUTC());
    }

    public static UUID generate(Clock clock) {
        long timestamp = clock.millis() & 0xFFFF_FFFF_FFFFL;
        long randA = RANDOM.nextLong() & 0x0FFFL;
        long randB = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        long msb = (timestamp << 16) | 0x7000L | randA;
        long lsb = 0x8000_0000_0000_0000L | randB;
        return new UUID(msb, lsb);
    }
}
