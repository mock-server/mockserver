package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.mockserver.openapi.OpenAPIParser.mapOperations;
import static org.slf4j.event.Level.ERROR;

public class OpenAPISerialiser {

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();
    private final MockServerLogger mockServerLogger;

    public OpenAPISerialiser(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String asString(OpenAPIDefinition openAPIDefinition) {
        try {
            if (isBlank(openAPIDefinition.getOperationId())) {
                return OBJECT_WRITER.writeValueAsString(buildOpenAPI(openAPIDefinition.getSpecUrlOrPayload(), mockServerLogger));
            } else {
                Optional<Pair<String, Operation>> operation = retrieveOperation(openAPIDefinition.getSpecUrlOrPayload(), openAPIDefinition.getOperationId());
                if (operation.isPresent()) {
                    return operation.get().getLeft() + ": " + OBJECT_WRITER.writeValueAsString(operation.get().getRight());
                }
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while serialising specification for OpenAPIDefinition{}")
                    .setArguments(openAPIDefinition)
                    .setThrowable(throwable)
            );
        }
        return "";
    }

    public Optional<Pair<String, Operation>> retrieveOperation(String specUrlOrPayload, String operationId) {
        return buildOpenAPI(specUrlOrPayload, mockServerLogger)
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> mapOperations(pathItem).stream())
            .filter(operation -> isBlank(operationId) || operation.getRight().getOperationId().equals(operationId))
            .findFirst();
    }

    public Map<String, List<Pair<String, Operation>>> retrieveOperations(OpenAPI openAPI, String operationId) {
        Map<String, List<Pair<String, Operation>>> operations = new LinkedHashMap<>();
        if (openAPI != null) {
            openAPI
                .getPaths()
                .forEach((key, value) -> {
                    if (key != null && value != null) {
                        List<Pair<String, Operation>> filteredOperations = mapOperations(value)
                            .stream()
                            .filter(operation -> isBlank(operationId) || operationId.equals(operation.getRight().getOperationId()))
                            .sorted(Comparator.comparing(Pair::getLeft))
                            .collect(Collectors.toList());
                        if (!filteredOperations.isEmpty()) {
                            operations.put(key, filteredOperations);
                        }
                    }
                });
        }
        return operations;
    }

}
