package org.mockserver.openapi;

import com.atlassian.oai.validator.util.ContentTypeUtils;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ResolverFully;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.openapi.examples.ExampleBuilder;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.openapi.examples.models.Example;
import org.mockserver.openapi.examples.models.StringExample;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.matchers.OpenAPIMatcher.OPEN_API_LOAD_ERROR;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
import static org.slf4j.event.Level.ERROR;

public class OpenAPIConverter {

    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();
    private final MockServerEventLog mockServerLog;

    public OpenAPIConverter(MockServerEventLog mockServerLog) {
        this.mockServerLog = mockServerLog;
    }

    public List<Expectation> buildExpectations(String specUrlOrPayload, Map<String, String> operationsAndResponses) {
        OpenAPI openAPI = buildOpenAPI(specUrlOrPayload);
        return openAPI
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> pathItem
                .readOperations()
                .stream()
            )
            .filter(operation -> operationsAndResponses == null || operationsAndResponses.containsKey(operation.getOperationId()))
            .map(operation -> new Expectation(openAPI(specUrlOrPayload, operation.getOperationId()))
                .thenRespond(buildHttpResponse(openAPI, operation.getResponses(), operationsAndResponses != null ? operationsAndResponses.get(operation.getOperationId()) : null))
            )
            .collect(Collectors.toList());
    }

    public static Optional<Operation> retrieveOperation(String specUrlOrPayload, String operationId) {
        OpenAPI openAPI = buildOpenAPI(specUrlOrPayload);
        return openAPI
            .getPaths()
            .values()
            .stream()
            .flatMap(pathItem -> pathItem
                .readOperations()
                .stream()
            )
            .filter(operation -> operationId == null || operation.getOperationId().equals(operationId))
            .findFirst();
    }

    public static OpenAPI buildOpenAPI(String specUrlOrPayload) {
        if (specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml")) {
            try {
                return resolve(new OpenAPIV3Parser().read(specUrlOrPayload));
            } catch (Throwable throwable) {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + throwable.getMessage());
            }
        } else {
            SwaggerParseResult swaggerParseResult = new OpenAPIV3Parser().readContents(specUrlOrPayload);
            if (swaggerParseResult.getOpenAPI() != null) {
                return resolve(swaggerParseResult.getOpenAPI());
            } else {
                throw new IllegalArgumentException(OPEN_API_LOAD_ERROR + String.join(" and ", swaggerParseResult.getMessages()).trim());
            }
        }
    }

    private static OpenAPI resolve(OpenAPI openAPI) {
        openAPI = new OpenAPIResolver(openAPI).resolve();
        new ResolverFully().resolveFully(openAPI);
        return openAPI;
    }

    private HttpResponse buildHttpResponse(OpenAPI openAPI, ApiResponses apiResponses, String apiResponseKey) {
        HttpResponse response = response();
        Optional
            .ofNullable(apiResponses)
            .flatMap(notNullApiResponses -> notNullApiResponses.entrySet().stream().filter(entry -> isBlank(apiResponseKey) | entry.getKey().equals(apiResponseKey)).findFirst())
            .ifPresent(apiResponse -> {
                if (!apiResponse.getKey().equalsIgnoreCase("default")) {
                    response.withStatusCode(Integer.parseInt(apiResponse.getKey()));
                }
                Optional
                    .ofNullable(apiResponse.getValue().getHeaders())
                    .map(Map::entrySet)
                    .map(Set::stream)
                    .ifPresent(stream -> stream
                        .forEach(entry -> {
                            Header value = entry.getValue();
                            if (value.getExample() != null) {
                                response.withHeader(entry.getKey(), String.valueOf(value.getExample()));
                            } else if (value.getSchema() != null) {
                                Example example = ExampleBuilder.fromSchema(value.getSchema(), openAPI.getComponents().getSchemas());
                                if (example instanceof StringExample) {
                                    response.withHeader(entry.getKey(), ((StringExample) example).getValue());
                                } else {
                                    response.withHeader(entry.getKey(), serialise(example));
                                }
                            }
                        })
                    );
                Optional
                    .ofNullable(apiResponse.getValue().getContent())
                    .flatMap(content -> content
                        .entrySet()
                        .stream()
                        .findFirst()
                    )
                    .ifPresent(contentType -> {
                        response.withHeader("content-type", contentType.getKey());
                        Optional
                            .ofNullable(contentType.getValue())
                            .flatMap(mediaType -> Optional.ofNullable(mediaType.getSchema()))
                            .ifPresent(schema -> {
                                String serialise = serialise(ExampleBuilder.fromSchema(schema, openAPI.getComponents().getSchemas()));
                                if (ContentTypeUtils.isJsonContentType(contentType.getKey())) {
                                    response.withBody(json(serialise));
                                } else {
                                    response.withBody(serialise);
                                }
                            });
                    });
            });
        return response;
    }

    private String serialise(Object example) {
        try {
            return OBJECT_WRITER.writeValueAsString(example);
        } catch (Throwable throwable) {
            mockServerLog.add(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("Exception while serialising " + example.getClass() + " {}")
                    .setArguments(example)
                    .setThrowable(throwable)
            );
            return "";
        }
    }
}
