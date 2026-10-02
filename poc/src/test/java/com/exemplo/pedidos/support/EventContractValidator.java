package com.exemplo.pedidos.support;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Valida payloads de eventos contra os schemas de {@code docs/asyncapi/order-events.yaml}.
 *
 * <p>Não há validador AsyncAPI confiável em Java; o schema do payload é carregado pelo ponteiro
 * JSON dentro do documento AsyncAPI, com {@code $ref} internos resolvidos, e validado como JSON
 * Schema draft-07 (formato padrão de schema do AsyncAPI 3.0).
 */
public final class EventContractValidator {

    public static final EventContractValidator ORDER_CREATED = new EventContractValidator("OrderCreatedPayload");

    private final Schema schema;

    /** Identificador lógico do documento; o conteúdo vem do arquivo do repositório. */
    private static final String DOCUMENT_ID = "https://contratos.exemplo.com/asyncapi/order-events.yaml";

    private EventContractValidator(String schemaName) {
        String document = read(Path.of("docs/asyncapi/order-events.yaml"));
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7,
                        builder -> builder.schemas(Map.of(DOCUMENT_ID, document)))
                .getSchema(SchemaLocation.of(DOCUMENT_ID + "#/components/schemas/" + schemaName));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Error> validate(String json) {
        return schema.validate(json, InputFormat.JSON);
    }

    public void assertValid(String json) {
        List<Error> errors = validate(json);
        if (!errors.isEmpty()) {
            throw new AssertionError("Payload viola order-events.yaml: %s%nPayload: %s".formatted(errors, json));
        }
    }
}
