package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationIdValidator;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaExpectationIdValidator.jsonSchemaExpectationIdValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class ExpectationIdSerializer implements Serializer<ExpectationId> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaExpectationIdValidator expectationIdValidator;

    public ExpectationIdSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    private JsonSchemaExpectationIdValidator getValidator() {
        if (expectationIdValidator == null) {
            expectationIdValidator = jsonSchemaExpectationIdValidator(mockServerLogger);
        }
        return expectationIdValidator;
    }

    public String serialize(ExpectationId expectationId) {
        try {
            return objectWriter.writeValueAsString(expectationId);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing ExpectationId to JSON with value " + expectationId)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing ExpectationId to JSON with value " + expectationId, e);
        }
    }

    public String serialize(List<? extends ExpectationId> expectationIds) {
        return serialize(false, expectationIds);
    }

    public String serialize(boolean prettyPrint, List<? extends ExpectationId> expectationIds) {
        return serialize(prettyPrint, expectationIds.toArray(new ExpectationId[0]));
    }

    public String serialize(ExpectationId... expectationIds) {
        return serialize(false, expectationIds);
    }

    public String serialize(boolean prettyPrint, ExpectationId... expectationIds) {
        try {
            if (expectationIds != null && expectationIds.length > 0) {
                return objectWriter.writeValueAsString(expectationIds);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing ExpectationId to JSON with value " + Arrays.asList(expectationIds))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing ExpectationId to JSON with value " + Arrays.asList(expectationIds), e);
        }
    }

    public ExpectationId deserialize(String jsonExpectationId) {
        if (isBlank(jsonExpectationId)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE
                    + " - a request is required but value was \"" + jsonExpectationId + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            if (jsonExpectationId.contains("\"httpRequest\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonExpectationId);
                    if (jsonNode.has("httpRequest")) {
                        jsonExpectationId = jsonNode.get("httpRequest").toString();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for ExpectationId " + throwable.getMessage())
                            .setArguments(jsonExpectationId)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonExpectationId + "] for ExpectationId", throwable);
                }
            } else if (jsonExpectationId.contains("\"openAPIDefinition\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonExpectationId);
                    if (jsonNode.has("openAPIDefinition")) {
                        jsonExpectationId = jsonNode.get("openAPIDefinition").toString();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for ExpectationId " + throwable.getMessage())
                            .setArguments(jsonExpectationId)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonExpectationId + "] for ExpectationId", throwable);
                }
            }
            String validationErrors = getValidator().isValid(jsonExpectationId);
            if (validationErrors.isEmpty()) {
                ExpectationId expectationId;
                try {
                    expectationId = objectMapper.readValue(jsonExpectationId, ExpectationId.class);
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for ExpectationId " + throwable.getMessage())
                            .setArguments(jsonExpectationId)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonExpectationId + "] for ExpectationId", throwable);
                }
                return expectationId;
            } else {
                throw new IllegalArgumentException(StringUtils.removeEndIgnoreCase(formatLogMessage("incorrect request matcher json format for:{}schema validation errors:{}", jsonExpectationId, validationErrors), "\n"));
            }
        }
    }

    @Override
    public Class<ExpectationId> supportsType() {
        return ExpectationId.class;
    }

    public ExpectationId[] deserializeArray(String jsonExpectationIds) {
        List<ExpectationId> expectationIds = new ArrayList<>();
        if (isBlank(jsonExpectationIds)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or request array is required but value was \"" + jsonExpectationIds + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.returnJSONObjects(jsonExpectationIds);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or array of request is required");
            } else {
                List<String> validationErrorsList = new ArrayList<>();
                for (String jsonRequest : jsonRequestList) {
                    try {
                        expectationIds.add(deserialize(jsonRequest));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on("," + NEW_LINE).join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return expectationIds.toArray(new ExpectationId[0]);
    }

}
