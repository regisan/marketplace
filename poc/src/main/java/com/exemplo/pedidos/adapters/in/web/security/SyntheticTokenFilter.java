package com.exemplo.pedidos.adapters.in.web.security;

import com.exemplo.pedidos.application.Caller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticação simplificada: resolve {@code Authorization: Bearer <token>} pela tabela de tokens
 * sintéticos. Token ausente ou desconhecido retorna {@code 401} no formato de erro da versão da API.
 */
public class SyntheticTokenFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTRIBUTE = Caller.class.getName();

    private static final String BEARER = "Bearer ";

    private final SyntheticTokenProperties properties;

    public SyntheticTokenFilter(SyntheticTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/orders") || path.startsWith("/v1/") || path.startsWith("/v2/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        SyntheticTokenProperties.Identity identity = header != null && header.startsWith(BEARER)
                ? properties.tokens().get(header.substring(BEARER.length()).trim())
                : null;
        if (identity == null) {
            unauthorized(request, response);
            return;
        }
        request.setAttribute(CALLER_ATTRIBUTE, new Caller(identity.callerId(), identity.type(),
                identity.partnerId(), identity.channel(), identity.country()));
        chain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (request.getRequestURI().startsWith("/v2/")) {
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                    + "\"detail\":\"Token ausente, inválido ou expirado.\"}");
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Token ausente, inválido ou expirado.\"}");
        }
    }
}
