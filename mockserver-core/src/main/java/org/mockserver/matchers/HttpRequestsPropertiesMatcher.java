package org.mockserver.matchers;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.openapi.OpenAPISerialiser;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.jar.Attributes.Name.CONTENT_TYPE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.ERROR;

public class HttpRequestsPropertiesMatcher extends AbstractHttpRequestMatcher {
    public static final String OPEN_API_LOAD_ERROR = "Unable to load API spec from provided URL or payload ";
    private int hashCode;
    private OpenAPIDefinition openAPIDefinition;
    private List<HttpRequestPropertiesMatcher> httpRequestPropertiesMatchers;
    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();

    protected HttpRequestsPropertiesMatcher(MockServerLogger mockServerLogger) {
        super(mockServerLogger);
    }

    @Override
    public boolean apply(RequestDefinition requestDefinition) {
        OpenAPIDefinition openAPIDefinition = requestDefinition instanceof OpenAPIDefinition ? (OpenAPIDefinition) requestDefinition : null;
        if (this.openAPIDefinition == null || !this.openAPIDefinition.equals(openAPIDefinition)) {
            this.openAPIDefinition = openAPIDefinition;
            if (openAPIDefinition != null && isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
                httpRequestPropertiesMatchers = new ArrayList<>();
                OpenAPISerialiser openAPISerialiser = new OpenAPISerialiser(mockServerLogger);
                try {
                    Map<String, List<Pair<String, Operation>>> stringListMap = openAPISerialiser.retrieveOperations(openAPIDefinition.getSpecUrlOrPayload(), openAPIDefinition.getOperationId());
                    stringListMap
                        .forEach((path, operations) -> operations
                            .forEach(methodOperationPair -> {
                                if (methodOperationPair.getValue().getRequestBody() != null) {
                                    methodOperationPair.getValue().getRequestBody().getContent().forEach((contentType, mediaType) -> {
                                        HttpRequest httpRequest = createHttpRequest(openAPIDefinition, path, methodOperationPair);
                                        httpRequest.withHeader(CONTENT_TYPE.toString(), contentType);
                                        try {
                                            httpRequest.withBody(jsonSchema(OBJECT_WRITER.writeValueAsString(mediaType.getSchema())));
                                        } catch (Throwable throwable) {
                                            mockServerLogger.logEvent(
                                                new LogEntry()
                                                    .setLogLevel(ERROR)
                                                    .setMessageFormat("exception while creating adding request body{}from schema{}")
                                                    .setArguments(mediaType.getSchema(), openAPIDefinition)
                                                    .setThrowable(throwable)
                                            );
                                        }
                                        addRequestMatcher(openAPIDefinition, methodOperationPair, httpRequest);
                                    });
                                } else {
                                    HttpRequest httpRequest = createHttpRequest(openAPIDefinition, path, methodOperationPair);
                                    addRequestMatcher(openAPIDefinition, methodOperationPair, httpRequest);
                                }
                            }));
                } catch (Throwable throwable) {
                    String message = (StringUtils.isBlank(throwable.getMessage()) || !throwable.getMessage().contains(OPEN_API_LOAD_ERROR.trim()) ? OPEN_API_LOAD_ERROR : "") + throwable.getMessage();
                    if (throwable instanceof OpenApiInteractionValidator.ApiLoadException) {
                        OpenApiInteractionValidator.ApiLoadException apiLoadException = (OpenApiInteractionValidator.ApiLoadException) throwable;
                        if (!apiLoadException.getParseMessages().isEmpty()) {
                            message += String.join(" and ", apiLoadException.getParseMessages()).trim();
                        }

                    }
                    throw new IllegalArgumentException(message);
                }
            }
            this.hashCode = 0;
            return true;
        } else {
            return false;
        }
    }

    private void addRequestMatcher(OpenAPIDefinition openAPIDefinition, Pair<String, Operation> methodOperationPair, HttpRequest httpRequest) {
        HttpRequestPropertiesMatcher httpRequestPropertiesMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        httpRequestPropertiesMatcher.update(httpRequest);
        httpRequestPropertiesMatcher.setControlPlaneMatcher(controlPlaneMatcher);
        httpRequestPropertiesMatcher.setDescription("" +
            "for swagger " +
            (openAPIDefinition.getSpecUrlOrPayload().endsWith(".json") || openAPIDefinition.getSpecUrlOrPayload().endsWith(".yaml") ? "\"" + openAPIDefinition.getSpecUrlOrPayload() + "\" " : "") +
            "operation \"" + methodOperationPair.getValue().getOperationId() + "\""
        );
        httpRequestPropertiesMatchers.add(httpRequestPropertiesMatcher);
    }

    private HttpRequest createHttpRequest(OpenAPIDefinition openAPIDefinition, String path, Pair<String, Operation> methodOperationPair) {
        HttpRequest httpRequest = new HttpRequest()
            .withMethod(methodOperationPair.getKey())
            .withPath(path);
        if (methodOperationPair.getValue().getParameters() != null) {
            for (Parameter parameter : methodOperationPair.getValue().getParameters()) {
                try {
                    switch (parameter.getIn()) {
                        case "query": {
                            httpRequest.withQueryStringParameter(string(parameter.getName()), schemaString(OBJECT_WRITER.writeValueAsString(parameter.getSchema())));
                            break;
                        }
                        case "header": {
                            httpRequest.withHeader(string(parameter.getName()), schemaString(OBJECT_WRITER.writeValueAsString(parameter.getSchema())));
                            break;
                        }
                        case "path": {
                            httpRequest.withPathParameter(string(parameter.getName()), schemaString(OBJECT_WRITER.writeValueAsString(parameter.getSchema())));
                            break;
                        }
                        case "cookie": {
                            httpRequest.withCookie(string(parameter.getName()), schemaString(OBJECT_WRITER.writeValueAsString(parameter.getSchema())));
                            break;
                        }
                        default:
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(ERROR)
                                    .setMessageFormat("unknown value for parameter in property, expected \"query\", \"header\", \"path\" or \"cookie\" found \"" + parameter.getIn() + "\"")
                            );
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("exception while creating adding parameter{}from schema{}")
                            .setArguments(parameter, openAPIDefinition)
                            .setThrowable(throwable)
                    );
                }
            }
        }
        return httpRequest;
    }

    @Override
    public boolean matches(MatchDifference matchDifference, RequestDefinition requestDefinition) {
        boolean result = false;
        if (httpRequestPropertiesMatchers != null && !httpRequestPropertiesMatchers.isEmpty()) {
            for (HttpRequestPropertiesMatcher httpRequestPropertiesMatcher : httpRequestPropertiesMatchers) {
                result = httpRequestPropertiesMatcher.matches(matchDifference, requestDefinition);
                if (result) {
                    break;
                }
            }
        } else {
            result = true;
        }
        return result;
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                .createObjectMapper(true)
                .writeValueAsString(openAPIDefinition);
        } catch (Exception e) {
            return super.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpRequestsPropertiesMatcher that = (HttpRequestsPropertiesMatcher) o;
        return Objects.equals(openAPIDefinition, that.openAPIDefinition);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), openAPIDefinition);
        }
        return hashCode;
    }
}
