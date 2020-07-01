package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;
import org.mockserver.serialization.model.RequestDefinitionDTO;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.validator.jsonschema.JsonSchemaRequestDefinitionValidator;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaRequestDefinitionValidator.jsonSchemaRequestDefinitionValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class RequestDefinitionSerializer implements Serializer<RequestDefinition> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaRequestDefinitionValidator requestDefinitionValidator;

    public RequestDefinitionSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    private JsonSchemaRequestDefinitionValidator getValidator() {
        if (requestDefinitionValidator == null) {
            requestDefinitionValidator = jsonSchemaRequestDefinitionValidator(mockServerLogger);
        }
        return requestDefinitionValidator;
    }

    public String serialize(RequestDefinition requestDefinition) {
        return serialize(false, requestDefinition);
    }

    public String serialize(boolean prettyPrint, RequestDefinition requestDefinition) {
        try {
            if (requestDefinition instanceof HttpRequest) {
                return objectWriter.writeValueAsString(prettyPrint ? new HttpRequestTemplateObject((HttpRequest) requestDefinition) : new HttpRequestDTO((HttpRequest) requestDefinition));
            } else if (requestDefinition instanceof OpenAPIDefinition) {
                return objectWriter.writeValueAsString(new OpenAPIDefinitionDTO((OpenAPIDefinition) requestDefinition));
            } else {
                return "";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing RequestDefinition to JSON with value " + requestDefinition)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing RequestDefinition to JSON with value " + requestDefinition, e);
        }
    }

    public String serialize(List<? extends RequestDefinition> requestDefinitions) {
        return serialize(false, requestDefinitions);
    }

    public String serialize(boolean prettyPrint, List<? extends RequestDefinition> requestDefinitions) {
        return serialize(prettyPrint, requestDefinitions.toArray(new RequestDefinition[0]));
    }

    public String serialize(RequestDefinition... requestDefinitions) {
        return serialize(false, requestDefinitions);
    }

    public String serialize(boolean prettyPrint, RequestDefinition... requestDefinitions) {
        try {
            if (requestDefinitions != null && requestDefinitions.length > 0) {
                Object[] requestDefinitionDTOs = new Object[requestDefinitions.length];
                for (int i = 0; i < requestDefinitions.length; i++) {
                    if (requestDefinitions[i] instanceof HttpRequest) {
                        requestDefinitionDTOs[i] = prettyPrint ? new HttpRequestTemplateObject((HttpRequest) requestDefinitions[i]) : new HttpRequestDTO((HttpRequest) requestDefinitions[i]);
                    } else if (requestDefinitions[i] instanceof OpenAPIDefinition) {
                        requestDefinitionDTOs[i] = new OpenAPIDefinitionDTO((OpenAPIDefinition) requestDefinitions[i]);
                    }
                }
                return objectWriter.writeValueAsString(requestDefinitionDTOs);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing RequestDefinition to JSON with value " + Arrays.asList(requestDefinitions))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing RequestDefinition to JSON with value " + Arrays.asList(requestDefinitions), e);
        }
    }

    public RequestDefinition deserialize(String jsonRequestDefinition) {
        if (isBlank(jsonRequestDefinition)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE
                    + " - a request is required but value was \"" + jsonRequestDefinition + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            if (jsonRequestDefinition.contains("\"httpRequest\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonRequestDefinition);
                    if (jsonNode.has("httpRequest")) {
                        jsonRequestDefinition = jsonNode.get("httpRequest").toString();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for RequestDefinition " + throwable.getMessage())
                            .setArguments(jsonRequestDefinition)
                            .setThrowable(throwable)
                    );
                    throw new RuntimeException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
                }
            } else if (jsonRequestDefinition.contains("\"openAPIDefinition\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonRequestDefinition);
                    if (jsonNode.has("openAPIDefinition")) {
                        jsonRequestDefinition = jsonNode.get("openAPIDefinition").toString();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for RequestDefinition " + throwable.getMessage())
                            .setArguments(jsonRequestDefinition)
                            .setThrowable(throwable)
                    );
                    throw new RuntimeException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
                }
            }
            String validationErrors = getValidator().isValid(jsonRequestDefinition);
            if (validationErrors.isEmpty()) {
                RequestDefinition requestDefinition = null;
                try {
                    RequestDefinitionDTO requestDefinitionDTO = objectMapper.readValue(jsonRequestDefinition, RequestDefinitionDTO.class);
                    if (requestDefinitionDTO != null) {
                        requestDefinition = requestDefinitionDTO.buildObject();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for RequestDefinition " + throwable.getMessage())
                            .setArguments(jsonRequestDefinition)
                            .setThrowable(throwable)
                    );
                    throw new RuntimeException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
                }
                return requestDefinition;
            } else {
                throw new IllegalArgumentException(StringUtils.removeEndIgnoreCase(formatLogMessage("incorrect request matcher json format for:{}schema validation errors:{}", jsonRequestDefinition, validationErrors), "\n"));
            }
        }
    }

    @Override
    public Class<RequestDefinition> supportsType() {
        return RequestDefinition.class;
    }

    public RequestDefinition[] deserializeArray(String jsonRequestDefinitions) {
        List<RequestDefinition> requestDefinitions = new ArrayList<>();
        if (isBlank(jsonRequestDefinitions)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or request array is required but value was \"" + jsonRequestDefinitions + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.returnJSONObjects(jsonRequestDefinitions);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or array of request is required");
            } else {
                List<String> validationErrorsList = new ArrayList<>();
                for (String jsonRequest : jsonRequestList) {
                    try {
                        requestDefinitions.add(deserialize(jsonRequest));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on("," + NEW_LINE).join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return requestDefinitions.toArray(new RequestDefinition[0]);
    }

}
