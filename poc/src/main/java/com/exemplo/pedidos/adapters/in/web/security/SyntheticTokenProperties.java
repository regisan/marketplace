package com.exemplo.pedidos.adapters.in.web.security;

import com.exemplo.pedidos.application.CallerType;
import com.exemplo.pedidos.domain.Channel;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tabela de tokens sintéticos ({@code pedidos.auth.tokens}). Substitui a validação de JWT e o IdP,
 * que ficam fora da PoC. Só existe nos perfis {@code test} e {@code local}.
 */
@ConfigurationProperties("pedidos.auth")
public record SyntheticTokenProperties(Map<String, Identity> tokens) {

    public SyntheticTokenProperties {
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
    }

    public record Identity(String callerId, CallerType type, String partnerId, Channel channel, String country) {
    }
}
