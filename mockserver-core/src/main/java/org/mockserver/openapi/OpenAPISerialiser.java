package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ResolverFully;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.matchers.OpenAPIMatcher.OPEN_API_LOAD_ERROR;
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
                return OBJECT_WRITER.writeValueAsString(buildOpenAPI(openAPIDefinition.getSpecUrlOrPayload(), false));
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

    private Optional<Pair<String, Operation>> retrieveOperation(String specUrlOrPayload, String operationId) {
        return buildOpenAPI(specUrlOrPayload, true)
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> mapOperations(pathItem).stream())
            .filter(operation -> isBlank(operationId) || operation.getRight().getOperationId().equals(operationId))
            .findFirst();
    }

    private List<Pair<String, Operation>> mapOperations(PathItem pathItem) {
        List<Pair<String, Operation>> allOperations = new ArrayList<>();
        if (pathItem.getGet() != null) {
            allOperations.add(new ImmutablePair<>("GET", pathItem.getGet()));
        }
        if (pathItem.getPut() != null) {
            allOperations.add(new ImmutablePair<>("PUT", pathItem.getPut()));
        }
        if (pathItem.getHead() != null) {
            allOperations.add(new ImmutablePair<>("HEAD", pathItem.getHead()));
        }
        if (pathItem.getPost() != null) {
            allOperations.add(new ImmutablePair<>("POST", pathItem.getPost()));
        }
        if (pathItem.getDelete() != null) {
            allOperations.add(new ImmutablePair<>("DELETE", pathItem.getDelete()));
        }
        if (pathItem.getPatch() != null) {
            allOperations.add(new ImmutablePair<>("PATCH", pathItem.getPatch()));
        }
        if (pathItem.getOptions() != null) {
            allOperations.add(new ImmutablePair<>("OPTIONS", pathItem.getOptions()));
        }
        if (pathItem.getTrace() != null) {
            allOperations.add(new ImmutablePair<>("TRACE", pathItem.getTrace()));
        }
        return allOperations;
    }

    private OpenAPI buildOpenAPI(String specUrlOrPayload, boolean resolve) {
        if (specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml")) {
            try {
                OpenAPI openAPI = new OpenAPIV3Parser().read(specUrlOrPayload);
                if (resolve) {
                    return resolve(openAPI);
                } else {
                    return openAPI;
                }
            } catch (Throwable throwable) {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + throwable.getMessage());
            }
        } else {
            SwaggerParseResult swaggerParseResult = new OpenAPIV3Parser().readContents(specUrlOrPayload);
            OpenAPI openAPI = swaggerParseResult.getOpenAPI();
            if (openAPI != null) {
                if (resolve) {
                    return resolve(openAPI);
                } else {
                    return openAPI;
                }
            } else {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + String.join(" and ", swaggerParseResult.getMessages()).trim());
            }
        }
    }

    private OpenAPI resolve(OpenAPI openAPI) {
        openAPI = new OpenAPIResolver(openAPI).resolve();
        new ResolverFully().resolveFully(openAPI);
        return openAPI;
    }
}
