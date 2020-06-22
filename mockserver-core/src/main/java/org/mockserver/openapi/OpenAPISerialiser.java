package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ResolverFully;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.*;
import java.util.stream.Collectors;

import static io.swagger.v3.parser.OpenAPIV3Parser.getExtensions;
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

    public Optional<Pair<String, Operation>> retrieveOperation(String specUrlOrPayload, String operationId) {
        return buildOpenAPI(specUrlOrPayload, true)
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> mapOperations(pathItem).stream())
            .filter(operation -> isBlank(operationId) || operation.getRight().getOperationId().equals(operationId))
            .findFirst();
    }

    public Map<String, List<Pair<String, Operation>>> retrieveOperations(String specUrlOrPayload, String operationId) {
        Map<String, List<Pair<String, Operation>>> operations = new HashMap<>();
        OpenAPI openAPI = buildOpenAPI(specUrlOrPayload, true);
        if (openAPI != null) {
            openAPI
                .getPaths()
                .forEach((key, value) -> {
                    if (key != null && value != null) {
                        List<Pair<String, Operation>> filteredOperations = mapOperations(value)
                            .stream()
                            .filter(operation -> isBlank(operationId) || operationId.equals(operation.getRight().getOperationId()))
                            .collect(Collectors.toList());
                        if (!filteredOperations.isEmpty()) {
                            operations.put(key, filteredOperations);
                        }
                    }
                });
        }
        return operations;
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

    public OpenAPI buildOpenAPI(String specUrlOrPayload, boolean resolve) {
        OpenAPI openAPI = null;
        SwaggerParseResult swaggerParseResult = null;
        List<AuthorizationValue> auths = null;
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);
        parseOptions.setResolveCombinators(true);
        parseOptions.setFlatten(true);
        parseOptions.setFlattenComposedSchemas(true);

        try {
            if (specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml")) {
                specUrlOrPayload = specUrlOrPayload.replaceAll("\\\\", "/");
                List<SwaggerParserExtension> parserExtensions = getExtensions();
                for (SwaggerParserExtension extension : parserExtensions) {
                    swaggerParseResult = extension.readLocation(specUrlOrPayload, auths, parseOptions);
                    openAPI = swaggerParseResult.getOpenAPI();
                    if (openAPI != null) {
                        break;
                    }
                }
            } else {
                swaggerParseResult = new OpenAPIV3Parser().readContents(specUrlOrPayload, auths, parseOptions);
                openAPI = swaggerParseResult.getOpenAPI();
            }
        } catch (Throwable throwable) {
            throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + (isNotBlank(throwable.getMessage()) ? throwable.getMessage() : ""), throwable);
        }
        if (openAPI != null) {
            if (resolve) {
                try {
                    return resolve(openAPI, auths, specUrlOrPayload);
                } catch (Throwable throwable) {
                    throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + (isNotBlank(throwable.getMessage()) ? throwable.getMessage() : ""), throwable);
                }
            } else {
                return openAPI;
            }
        } else {
            if (swaggerParseResult != null) {
                List<String> messages = swaggerParseResult.getMessages().stream().filter(Objects::nonNull).collect(Collectors.toList());
                throw new IllegalArgumentException((OPEN_API_LOAD_ERROR + String.join(" and ", messages).trim()).trim());
            } else {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR.trim());
            }
        }
    }

    private OpenAPI resolve(OpenAPI openAPI, List<AuthorizationValue> auths, String specUrlOrPayload) {
        if (openAPI != null) {
            OpenAPIResolver.Settings settings = new OpenAPIResolver.Settings();
            settings.addParametersToEachOperation(true);
            openAPI = new OpenAPIResolver(openAPI, auths, specUrlOrPayload, settings).resolve();
            new ResolverFully().resolveFully(openAPI);
        }
        return openAPI;
    }
}
