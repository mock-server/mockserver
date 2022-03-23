package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.fge.jackson.JacksonUtils;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator.jsonSchemaExpectationValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class ExpectationSerializer implements Serializer<Expectation> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter;
    private ObjectMapper objectMapper;
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaExpectationValidator expectationValidator;
    private OpenAPIExpectationSerializer openAPIExpectationSerializer;
    private static boolean printedECMA262Warning = false;

    public ExpectationSerializer(MockServerLogger mockServerLogger) {
        this(mockServerLogger, false);
    }

    public ExpectationSerializer(MockServerLogger mockServerLogger, boolean serialiseDefaultValues) {
        this.mockServerLogger = mockServerLogger;
        this.openAPIExpectationSerializer = new OpenAPIExpectationSerializer(mockServerLogger);
        this.objectWriter = ObjectMapperFactory.createObjectMapper(true, serialiseDefaultValues);
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
    }

    private JsonSchemaExpectationValidator getValidator() {
        if (expectationValidator == null) {
            if (!printedECMA262Warning) {
                // output warning if Java 11+ due to deprecation warning from Nashorn
                if (!System.getProperty("java.version").contains("1.8") && !System.getProperty("java.version").contains("1.9")) {
                    try {
                        this.getClass().getClassLoader().loadClass("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
                        System.err.println("Loading JavaScript to validate ECMA262 regular expression in JsonSchema because java.util.regex package in Java does not match ECMA262");
                    } catch (ClassNotFoundException ignore) {
                    }
                }
                printedECMA262Warning = true;
            }
            expectationValidator = jsonSchemaExpectationValidator(mockServerLogger);
        }
        return expectationValidator;
    }

    public String serialize(Expectation expectation) {
        if (expectation != null) {
            try {
                return objectWriter
                    .writeValueAsString(new ExpectationDTO(expectation));
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while serializing expectation to JSON with value " + expectation)
                        .setThrowable(e)
                );
                throw new RuntimeException("Exception while serializing expectation to JSON with value " + expectation, e);
            }
        } else {
            return "";
        }
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[0]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectations.length];
                for (int i = 0; i < expectations.length; i++) {
                    expectationDTOs[i] = new ExpectationDTO(expectations[i]);
                }
                return objectWriter
                    .writeValueAsString(expectationDTOs);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing expectation to JSON with value " + Arrays.asList(expectations))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations), e);
        }
    }

    public Expectation deserialize(String jsonExpectation) {
        if (isBlank(jsonExpectation)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE
                    + " - an expectation is required but value was \"" + jsonExpectation + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            String validationErrors = getValidator().isValid(jsonExpectation);
            if (validationErrors.isEmpty()) {
                Expectation expectation = null;
                try {
                    ExpectationDTO expectationDTO = objectMapper.readValue(jsonExpectation, ExpectationDTO.class);
                    if (expectationDTO != null) {
                        expectation = expectationDTO.buildObject();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for Expectation " + throwable.getMessage())
                            .setArguments(jsonExpectation)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonExpectation + "] for Expectation", throwable);
                }
                return expectation;
            } else {
                throw new IllegalArgumentException(StringUtils.removeEndIgnoreCase(formatLogMessage("incorrect expectation json format for:{}schema validation errors:{}", jsonExpectation, validationErrors), "\n"));
            }
        }
    }

    @Override
    public Class<Expectation> supportsType() {
        return Expectation.class;
    }

    public Expectation[] deserializeArray(String jsonExpectations, boolean allowEmpty) {
        List<Expectation> expectations = new ArrayList<>();
        if (isBlank(jsonExpectations)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation or expectation array is required but value was \"" + jsonExpectations + "\"");
        } else {
            List<String> validationErrorsList = new ArrayList<String>();
            List<JsonNode> jsonExpectationList = jsonArraySerializer.splitJSONArrayToJSONNodes(jsonExpectations);
            if (!jsonExpectationList.isEmpty()) {
                for (int i = 0; i < jsonExpectationList.size(); i++) {
                    String jsonExpectation = JacksonUtils.prettyPrint(jsonExpectationList.get(i));
                    if (jsonExpectationList.size() > 100) {
                        if (MockServerLogger.isEnabled(DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(DEBUG)
                                    .setMessageFormat("processing JSON expectation " + (i + 1) + " of " + jsonExpectationList.size() + ":{}")
                                    .setArguments(jsonExpectation)
                            );
                        } else if (MockServerLogger.isEnabled(INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(INFO)
                                    .setMessageFormat("processing JSON expectation " + (i + 1) + " of " + jsonExpectationList.size())
                            );
                        }
                    }
                    if (jsonExpectationList.get(i).has("specUrlOrPayload")) {
                        try {
                            expectations.addAll(openAPIExpectationSerializer.deserializeToExpectations(jsonExpectation));
                        } catch (IllegalArgumentException iae) {
                            validationErrorsList.add(iae.getMessage());
                        }
                    } else {
                        try {
                            expectations.add(deserialize(jsonExpectation));
                        } catch (IllegalArgumentException iae) {
                            validationErrorsList.add(iae.getMessage());
                        }
                    }
                }
                if (!validationErrorsList.isEmpty()) {
                    if (validationErrorsList.size() > 1) {
                        throw new IllegalArgumentException(("[" + NEW_LINE + Joiner.on("," + NEW_LINE + NEW_LINE).join(validationErrorsList)).replaceAll(NEW_LINE, NEW_LINE + "  ") + NEW_LINE + "]");
                    } else {
                        throw new IllegalArgumentException(validationErrorsList.get(0));
                    }
                }
            } else if (!allowEmpty) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation or array of expectations is required");
            }
        }
        return expectations.toArray(new Expectation[0]);
    }

}
