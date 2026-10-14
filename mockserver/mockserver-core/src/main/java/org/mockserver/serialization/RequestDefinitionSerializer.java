package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.Strings;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.model.HttpRequestPrettyPrintedDTO;
import org.mockserver.serialization.model.RequestDefinitionDTO;
import org.mockserver.validator.jsonschema.JsonSchemaRequestDefinitionValidator;
import org.slf4j.event.Level;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaRequestDefinitionValidator.jsonSchemaRequestDefinitionValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class RequestDefinitionSerializer implements Serializer<RequestDefinition> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
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
        if (requestDefinition == null) {
            return "";
        }
        Object requestDefinitionDTO = toDTO(prettyPrint, requestDefinition);
        try {
            return objectWriter.writeValueAsString(requestDefinitionDTO);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setHttpRequestIfPresent(requestDefinition)
                    .setMessageFormat("exception while serializing RequestDefinition to JSON with value:{}")
                    .setArguments(requestDefinition)
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
                return objectWriter.writeValueAsString(toDTOs(prettyPrint, requestDefinitions));
            } else {
                return "[]";
            }
        } catch (Exception e) {
            throw failure(requestDefinitions, e);
        }
    }

    /**
     * As {@link #serialize(List)}, writing the same text to {@code writer} rather than building it as one String.
     */
    public void serialize(List<? extends RequestDefinition> requestDefinitions, Writer writer) {
        write(objectWriter, false, requestDefinitions.toArray(new RequestDefinition[0]), writer);
    }

    private void write(ObjectWriter jsonWriter, boolean prettyPrint, RequestDefinition[] definitions, Writer writer) {
        try {
            if (definitions.length > 0) {
                jsonWriter.writeValue(writer, toDTOs(prettyPrint, definitions));
            } else {
                writer.write("[]");
            }
        } catch (Exception e) {
            throw failure(definitions, e);
        }
    }

    private static Object[] toDTOs(boolean prettyPrint, RequestDefinition[] requestDefinitions) {
        Object[] requestDefinitionDTOs = new Object[requestDefinitions.length];
        for (int i = 0; i < requestDefinitions.length; i++) {
            requestDefinitionDTOs[i] = toDTO(prettyPrint, requestDefinitions[i]);
        }
        return requestDefinitionDTOs;
    }

    private static Object toDTO(boolean prettyPrint, RequestDefinition requestDefinition) {
        if (prettyPrint && requestDefinition instanceof HttpRequest) {
            return new HttpRequestPrettyPrintedDTO((HttpRequest) requestDefinition);
        }
        return RequestDefinitionDTO.of(requestDefinition);
    }

    private RuntimeException failure(RequestDefinition[] requestDefinitions, Exception e) {
        String description = SerializationFailure.describe(requestDefinitions, e);
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequests(LogEntry.nonNull(requestDefinitions))
                .setMessageFormat("exception while serializing RequestDefinition to JSON:{}")
                .setArguments(description)
                .setThrowable(e)
        );
        return new RuntimeException("Exception while serializing RequestDefinition to JSON (" + description + ")", e);
    }

    /**
     * Serialise recorded requests for retrieval via retrieveRecordedRequests. This is the only serialisation path
     * that emits the original wire bytes (base64 {@code rawBytes}) when they differ from the canonical JSON value,
     * so a JSON request body retrieved over the wire round-trips byte-for-byte (#2374). All other serialisation
     * (matcher/expectation serialisation, verification diagnostic logs) goes through {@link #serialize} and stays
     * clean, because the {@code emitRawBytes} attribute is scoped to this call only.
     */
    public String serializeRecordedRequests(boolean prettyPrint, List<? extends RequestDefinition> requestDefinitions) {
        RequestDefinition[] definitions = requestDefinitions.toArray(new RequestDefinition[0]);
        try {
            if (definitions.length > 0) {
                return objectWriter.withAttribute("emitRawBytes", Boolean.TRUE).writeValueAsString(toDTOs(prettyPrint, definitions));
            } else {
                return "[]";
            }
        } catch (Exception e) {
            throw failure(definitions, e);
        }
    }

    /**
     * As {@link #serializeRecordedRequests(boolean, List)}, writing the same text to {@code writer} rather than
     * building it as one String.
     */
    public void serializeRecordedRequests(boolean prettyPrint, List<? extends RequestDefinition> requestDefinitions, Writer writer) {
        write(objectWriter.withAttribute("emitRawBytes", Boolean.TRUE), prettyPrint, requestDefinitions.toArray(new RequestDefinition[0]), writer);
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
                    throw new IllegalArgumentException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
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
                    throw new IllegalArgumentException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
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
                    throw new IllegalArgumentException("exception while parsing [" + jsonRequestDefinition + "] for RequestDefinition", throwable);
                }
                return requestDefinition;
            } else {
                throw new IllegalArgumentException(Strings.CI.removeEnd(formatLogMessage("incorrect request matcher json format for:{}schema validation errors:{}", jsonRequestDefinition, validationErrors), "\n"));
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
            List<String> jsonRequestList = jsonArraySerializer.splitJSONArray(jsonRequestDefinitions);
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
