package org.mockserver.openapi;

import com.atlassian.oai.validator.util.ContentTypeUtils;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.ResolverFully;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.openapi.examples.ExampleBuilder;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
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
    private final MockServerLogger mockServerLogger;

    public OpenAPIConverter(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
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
            .map(operation ->
                new Expectation(openAPI(specUrlOrPayload, operation.getOperationId()))
                    .thenRespond(buildHttpResponse(
                        openAPI,
                        operation.getResponses(),
                        operationsAndResponses != null ? operationsAndResponses.get(operation.getOperationId()) : null
                    ))
            )
            .collect(Collectors.toList());
    }

    public OpenAPI buildOpenAPI(String specUrlOrPayload) {
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

    private OpenAPI resolve(OpenAPI openAPI) {
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
                            Example example = findExample(value);
                            if (example != null) {
                                response.withHeader(entry.getKey(), String.valueOf(example.getValue()));
                            } else if (value.getSchema() != null) {
                                org.mockserver.openapi.examples.models.Example generatedExample = ExampleBuilder.fromSchema(value.getSchema(), openAPI.getComponents().getSchemas());
                                if (generatedExample instanceof StringExample) {
                                    response.withHeader(entry.getKey(), ((StringExample) generatedExample).getValue());
                                } else {
                                    response.withHeader(entry.getKey(), serialise(generatedExample));
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
                            .ifPresent(mediaType -> {
                                Example example = findExample(mediaType);
                                if (example != null) {
                                    if (ContentTypeUtils.isJsonContentType(contentType.getKey())) {
                                        response.withBody(json(serialise(example.getValue())));
                                    } else {
                                        response.withBody(String.valueOf(example.getValue()));
                                    }
                                } else if (mediaType.getSchema() != null) {
                                    org.mockserver.openapi.examples.models.Example generatedExample = ExampleBuilder.fromSchema(mediaType.getSchema(), openAPI.getComponents().getSchemas());
                                    if (generatedExample instanceof StringExample) {
                                        response.withBody(((StringExample) generatedExample).getValue());
                                    } else {
                                        String serialise = serialise(ExampleBuilder.fromSchema(mediaType.getSchema(), openAPI.getComponents().getSchemas()));
                                        if (ContentTypeUtils.isJsonContentType(contentType.getKey())) {
                                            response.withBody(json(serialise));
                                        } else {
                                            response.withBody(serialise);
                                        }
                                    }
                                }
                            });
                    });
            });
        return response;
    }

    private Example findExample(Header value) {
        Example example = null;
        if (value.getExample() instanceof Example) {
            example = (Example) value.getExample();
        } else if (value.getExamples() != null && !value.getExamples().isEmpty()) {
            example = value.getExamples().values().stream().findFirst().orElse(null);
        }
        return example;
    }

    private Example findExample(MediaType mediaType) {
        Example example = null;
        if (mediaType.getExample() instanceof Example) {
            example = (Example) mediaType.getExample();
        } else if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            example = mediaType.getExamples().values().stream().findFirst().orElse(null);
        }
        return example;
    }

    private String serialise(Object example) {
        try {
            return OBJECT_WRITER.writeValueAsString(example);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while serialising " + example.getClass() + " {}")
                    .setArguments(example)
                    .setThrowable(throwable)
            );
            return "";
        }
    }
}
