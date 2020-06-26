package org.mockserver.matchers;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.openapi.OpenAPISerialiser;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.openapi.OpenAPISerialiser.OPEN_API_LOAD_ERROR;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.ERROR;

public class HttpRequestsPropertiesMatcher extends AbstractHttpRequestMatcher {

    private static final ObjectWriter TO_STRING_OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(true);
    private int hashCode;
    private OpenAPIDefinition openAPIDefinition;
    private List<HttpRequestPropertiesMatcher> httpRequestPropertiesMatchers;
    private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writerWithDefaultPrettyPrinter();

    protected HttpRequestsPropertiesMatcher(MockServerLogger mockServerLogger) {
        super(mockServerLogger);
    }

    public List<HttpRequestPropertiesMatcher> getHttpRequestPropertiesMatchers() {
        return httpRequestPropertiesMatchers;
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
                    OpenAPI openAPI = openAPISerialiser.buildOpenAPI(openAPIDefinition.getSpecUrlOrPayload(), true);
                    final Map<String, List<Pair<String, Operation>>> stringListMap = openAPISerialiser.retrieveOperations(openAPI, openAPIDefinition.getOperationId());
                    stringListMap
                        .forEach((path, operations) -> operations
                            .forEach(methodOperationPair -> {
                                Operation operation = methodOperationPair.getValue();
                                if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                                    operation.getRequestBody().getContent().forEach(handleRequestBody(openAPIDefinition, openAPI, path, methodOperationPair, Boolean.TRUE.equals(operation.getRequestBody().getRequired())));
                                } else {
                                    HttpRequest httpRequest = createHttpRequest(openAPIDefinition, openAPI, path, methodOperationPair);
                                    addRequestMatcher(openAPIDefinition, methodOperationPair, httpRequest, "");
                                }
                            }));
                } catch (Throwable throwable) {
                    String message = (StringUtils.isBlank(throwable.getMessage()) || !throwable.getMessage().contains(OPEN_API_LOAD_ERROR) ? OPEN_API_LOAD_ERROR + (isNotBlank(throwable.getMessage()) ? ", " : "") : "") + throwable.getMessage();
                    if (throwable instanceof OpenApiInteractionValidator.ApiLoadException) {
                        OpenApiInteractionValidator.ApiLoadException apiLoadException = (OpenApiInteractionValidator.ApiLoadException) throwable;
                        if (!apiLoadException.getParseMessages().isEmpty()) {
                            message += String.join(" and ", apiLoadException.getParseMessages()).trim();
                        }

                    }
                    throw new IllegalArgumentException(message, throwable);
                }
            }
            this.hashCode = 0;
            if (MockServerLogger.isEnabled(DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setHttpRequest(requestDefinition)
                        .setMessageFormat("created request matcher{}for open api definition{}")
                        .setArguments(this, requestDefinition)
                );
            }
            return true;
        } else {
            return false;
        }
    }

    private HttpRequest createHttpRequest(OpenAPIDefinition openAPIDefinition, OpenAPI openAPI, String path, Pair<String, Operation> methodOperationPair) {
        HttpRequest httpRequest = new HttpRequest()
            .withMethod(methodOperationPair.getKey())
            .withPath(path);
        Operation operation = methodOperationPair.getValue();
        // parameters
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                Schema<?> schema = Optional
                    .ofNullable(parameter.getSchema())
                    .orElse(Optional
                        .ofNullable(parameter.getContent())
                        .flatMap(content -> content
                            .values()
                            .stream()
                            .map(MediaType::getSchema)
                            .findFirst()
                        )
                        .orElse(null)
                    );
                if (schema != null) {
                    try {
                        NottableString name = parameter.getRequired() != null && parameter.getRequired() ? string(parameter.getName()) : optionalString(parameter.getName());
                        if (parameter.getAllowEmptyValue() != null && parameter.getAllowEmptyValue()) {
                            schema.nullable(true);
                        }
                        if (Boolean.TRUE.equals(parameter.getAllowReserved())) {
                            throw new IllegalArgumentException("allowReserved field is not supported on parameters, found on operation: \"" + methodOperationPair.getRight().getOperationId() + "\" method: \"" + methodOperationPair.getLeft() + "\" parameter: \"" + name + "\" in: \"" + parameter.getIn() + "\"");
                        }
                        switch (parameter.getIn()) {
                            case "query": {
                                httpRequest.withQueryStringParameter(name, schemaString(OBJECT_WRITER.writeValueAsString(schema)));
                                break;
                            }
                            case "header": {
                                httpRequest.withHeader(name, schemaString(OBJECT_WRITER.writeValueAsString(schema)));
                                break;
                            }
                            case "path": {
                                httpRequest.withPathParameter(name, schemaString(OBJECT_WRITER.writeValueAsString(schema)));
                                break;
                            }
                            case "cookie": {
                                httpRequest.withCookie(name, schemaString(OBJECT_WRITER.writeValueAsString(schema)));
                                break;
                            }
                            default:
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(ERROR)
                                        .setMessageFormat("unknown value for parameter in property, expected \"query\", \"header\", \"path\" or \"cookie\" found \"" + parameter.getIn() + "\"")
                                );
                        }
                    } catch (IOException exception) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(ERROR)
                                .setMessageFormat("exception while creating adding parameter{}from schema{}")
                                .setArguments(parameter, openAPIDefinition)
                                .setThrowable(exception)
                        );
                    }
                }
            }
        }
        // security schemes
        Map<String, Set<String>> headerMatches = new HashMap<>();
        buildSecurityHeaderValues(openAPI, headerMatches, openAPI.getSecurity());
        buildSecurityHeaderValues(openAPI, headerMatches, methodOperationPair.getRight().getSecurity());
        if (!headerMatches.isEmpty()) {
            if (headerMatches.keySet().size() > 1) {
                for (Map.Entry<String, Set<String>> headerMatchEntry : headerMatches.entrySet()) {
                    httpRequest.withHeader("?" + headerMatchEntry.getKey(), Joiner.on("|").join(headerMatchEntry.getValue()));
                }
                httpRequest.withHeader(Joiner.on("|").join(headerMatches.keySet()), ".*");
            } else {
                httpRequest.withHeader(Joiner.on("|").join(headerMatches.keySet()), Joiner.on("|").join(headerMatches.values().stream().flatMap(Collection::stream).collect(Collectors.toList())));
            }
        }
        return httpRequest;
    }

    private void buildSecurityHeaderValues(OpenAPI openAPI, Map<String, Set<String>> headerMatches, List<SecurityRequirement> security) {
        if (security != null) {
            for (SecurityRequirement securityRequirement : security) {
                if (securityRequirement != null) {
                    for (String securityRequirementName : securityRequirement.keySet()) {
                        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null && openAPI.getComponents().getSecuritySchemes().get(securityRequirementName) != null) {
                            SecurityScheme securityScheme = openAPI.getComponents().getSecuritySchemes().get(securityRequirementName);
                            String scheme = securityScheme.getScheme();
                            switch (securityScheme.getType()) {
                                case APIKEY: {
                                    String headerName = securityScheme.getName();
                                    if (isNotBlank(headerName)) {
                                        Set<String> headerValues = headerMatches.get(headerName);
                                        String headerValue = ".+";
                                        if (headerValues != null) {
                                            headerValues.add(headerValue);
                                        } else {
                                            headerMatches.put(headerName, new HashSet<>(Collections.singletonList(headerValue)));
                                        }
                                    }
                                    break;
                                }
                                case HTTP:
                                case OAUTH2:
                                case OPENIDCONNECT: {
                                    String headerName = AUTHORIZATION.toString();
                                    Set<String> headerValues = headerMatches.get(headerName);
                                    String headerValue = (isNotBlank(scheme) ? scheme : "") + ".+";
                                    if (headerValues != null) {
                                        headerValues.add(headerValue);
                                    } else {
                                        headerMatches.put(headerName, new HashSet<>(Collections.singletonList(headerValue)));
                                    }
                                    break;
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private BiConsumer<String, MediaType> handleRequestBody(OpenAPIDefinition openAPIDefinition, OpenAPI openAPI, String path, Pair<String, Operation> methodOperationPair, Boolean required) {
        return (contentType, mediaType) -> {
            HttpRequest httpRequest = createHttpRequest(openAPIDefinition, openAPI, path, methodOperationPair);
            if (contentType.equals("multipart/form-data")) {
                throw new IllegalArgumentException("multipart form data is not supported on requestBody, found on operation: \"" + methodOperationPair.getRight().getOperationId() + "\" method: \"" + methodOperationPair.getLeft() + "\"");
            }
            if (!contentType.equals("*/*") && required) {
                // ensure that parameters added to the content type such as charset don't break the matching
                httpRequest.withHeader(CONTENT_TYPE.toString(), contentType.replaceAll("\\*", ".*") + ".*");
            }
            if (mediaType != null && mediaType.getSchema() != null) {
                if (mediaType.getEncoding() != null) {
                    throw new IllegalArgumentException("encoding is not supported on requestBody, found on operation: \"" + methodOperationPair.getRight().getOperationId() + "\" method: \"" + methodOperationPair.getLeft() + "\"");
                }
                try {
                    httpRequest.withBody(jsonSchema(OBJECT_WRITER.writeValueAsString(mediaType.getSchema())).withOptional(!required));
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("exception while creating adding request body{}from schema{}")
                            .setArguments(mediaType.getSchema(), openAPIDefinition)
                            .setThrowable(throwable)
                    );
                }
            }
            addRequestMatcher(openAPIDefinition, methodOperationPair, httpRequest, contentType);
        };
    }

    private void addRequestMatcher(OpenAPIDefinition openAPIDefinition, Pair<String, Operation> methodOperationPair, HttpRequest httpRequest, String contentType) {
        HttpRequestPropertiesMatcher httpRequestPropertiesMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        httpRequestPropertiesMatcher.update(httpRequest);
        httpRequestPropertiesMatcher.setControlPlaneMatcher(controlPlaneMatcher);
        httpRequestPropertiesMatcher.setDescription("" +
            "for swagger " +
            (openAPIDefinition.getSpecUrlOrPayload().endsWith(".json") || openAPIDefinition.getSpecUrlOrPayload().endsWith(".yaml") ? "\"" + openAPIDefinition.getSpecUrlOrPayload() + "\" " : "") +
            "operation \"" + methodOperationPair.getValue().getOperationId() + "\"" +
            (isNotBlank(contentType) ? " content-type \"" + contentType + "\"" : "")
        );
        httpRequestPropertiesMatchers.add(httpRequestPropertiesMatcher);
    }

    @Override
    public boolean matches(MatchDifference matchDifference, RequestDefinition requestDefinition) {
        boolean result = false;
        if (httpRequestPropertiesMatchers != null && !httpRequestPropertiesMatchers.isEmpty()) {
            for (HttpRequestPropertiesMatcher httpRequestPropertiesMatcher : httpRequestPropertiesMatchers) {
                if (matchDifference == null) {
                    if (MockServerLogger.isEnabled(DEBUG) && requestDefinition instanceof HttpRequest) {
                        matchDifference = new MatchDifference((HttpRequest) requestDefinition);
                    }
                    result = httpRequestPropertiesMatcher.matches(matchDifference, requestDefinition);
                } else {
                    MatchDifference singleMatchDifference = new MatchDifference(matchDifference.getHttpRequest());
                    result = httpRequestPropertiesMatcher.matches(singleMatchDifference, requestDefinition);
                    matchDifference.addDifferences(singleMatchDifference.getAllDifferences());
                }
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
            return TO_STRING_OBJECT_WRITER
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
