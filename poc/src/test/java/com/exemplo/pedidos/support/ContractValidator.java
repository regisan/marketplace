package com.exemplo.pedidos.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Valida respostas HTTP contra um contrato OpenAPI 3.1 de {@code poc/docs/openapi}.
 *
 * <p>Os caminhos são passados sem o prefixo de versão ({@code /orders/{id}}), como estão
 * declarados nos contratos.
 */
public final class ContractValidator {

    public static final ContractValidator V2 = new ContractValidator("docs/openapi/orders-v2.yaml");
    public static final ContractValidator V1 = new ContractValidator("docs/openapi/orders-v1.yaml");
    public static final ContractValidator V1_BASELINE =
            new ContractValidator("docs/openapi/baseline/orders-v1.0.0.yaml");

    private final OpenApiInteractionValidator validator;

    private ContractValidator(String specPath) {
        this.validator = OpenApiInteractionValidator
                .createForSpecificationUrl(Path.of(specPath).toAbsolutePath().toUri().toString())
                .withBasePathOverride("/")
                .build();
    }

    public ValidationReport validate(String method, String path, int status,
            Map<String, List<String>> headers, String body) {
        SimpleResponse.Builder response = SimpleResponse.Builder.status(status);
        headers.forEach((name, values) -> response.withHeader(name, values));
        if (body != null && !body.isEmpty()) {
            response.withBody(body);
        }
        return validator.validateResponse(path, Request.Method.valueOf(method), response.build());
    }

    /** Lança {@link AssertionError} com as mensagens do relatório se houver erros. */
    public void assertValid(String method, String path, int status,
            Map<String, List<String>> headers, String body) {
        ValidationReport report = validate(method, path, status, headers, body);
        if (report.hasErrors()) {
            throw new AssertionError("Resposta %s %s %d viola o contrato:%n%s%nCorpo: %s"
                    .formatted(method, path, status, report.getMessages(), body));
        }
    }
}
