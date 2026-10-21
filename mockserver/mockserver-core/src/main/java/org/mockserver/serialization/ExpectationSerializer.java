package org.mockserver.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.Strings;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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
    private ObjectWriter openTargetObjectWriter;
    private ObjectMapper objectMapper;
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaExpectationValidator expectationValidator;
    private OpenAPIExpectationSerializer openAPIExpectationSerializer;
    private static boolean printedECMA262Warning = false;

    public ExpectationSerializer(MockServerLogger mockServerLogger) {
        this(mockServerLogger, false);
    }

    public ExpectationSerializer(MockServerLogger mockServerLogger, boolean serialiseDefaultValues) {
        this(mockServerLogger, serialiseDefaultValues, null);
    }

    /**
     * @param configuration the running server's configuration, which applies to what parsing the spec of an OpenAPI
     *                      expectation fetches; null only where no server is running (a client), for the global properties
     */
    public ExpectationSerializer(MockServerLogger mockServerLogger, @Nullable Configuration configuration) {
        this(mockServerLogger, false, configuration);
    }

    public ExpectationSerializer(MockServerLogger mockServerLogger, boolean serialiseDefaultValues, @Nullable Configuration configuration) {
        this.mockServerLogger = mockServerLogger;
        this.openAPIExpectationSerializer = new OpenAPIExpectationSerializer(mockServerLogger, configuration);
        this.objectWriter = ObjectMapperFactory.createObjectMapper(true, serialiseDefaultValues);
        this.openTargetObjectWriter = objectWriter.without(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
    }

    private JsonSchemaExpectationValidator getValidator() {
        if (expectationValidator == null) {
            if (!printedECMA262Warning) {
                try {
                    this.getClass().getClassLoader().loadClass("org.graalvm.polyglot.Context");
                    System.err.println("Loading JavaScript to validate ECMA262 regular expression in JsonSchema because java.util.regex package in Java does not match ECMA262");
                } catch (ClassNotFoundException ignore) {
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
                throw failure(expectation, e);
            }
        } else {
            return "";
        }
    }

    /**
     * As {@link #serialize(Expectation)}, writing the same text to {@code writer}, which is left open.
     */
    public void serialize(Expectation expectation, Writer writer) {
        if (expectation != null) {
            try {
                openTargetObjectWriter.writeValue(writer, new ExpectationDTO(expectation));
            } catch (Exception e) {
                throw failure(expectation, e);
            }
        }
    }

    private RuntimeException failure(Expectation expectation, Exception e) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception while serializing expectation to JSON with value:{}")
                .setArguments(expectation)
                .setThrowable(e)
        );
        return new RuntimeException("Exception while serializing expectation to JSON with value " + expectation, e);
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[0]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                return objectWriter
                    .writeValueAsString(toDTOs(expectations));
            } else {
                return "[]";
            }
        } catch (Exception e) {
            throw failure(expectations, e);
        }
    }

    /**
     * As {@link #serialize(List)}, writing the same text to {@code writer} rather than building it as one String.
     */
    public void serialize(List<Expectation> expectations, Writer writer) {
        Expectation[] array = expectations.toArray(new Expectation[0]);
        try {
            if (array.length > 0) {
                objectWriter.writeValue(writer, toDTOs(array));
            } else {
                writer.write("[]");
            }
        } catch (Exception e) {
            throw failure(array, e);
        }
    }

    private static ExpectationDTO[] toDTOs(Expectation[] expectations) {
        ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectations.length];
        for (int i = 0; i < expectations.length; i++) {
            expectationDTOs[i] = new ExpectationDTO(expectations[i]);
        }
        return expectationDTOs;
    }

    private RuntimeException failure(Expectation[] expectations, Exception e) {
        String description = SerializationFailure.describe(expectations, e);
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception while serializing expectation to JSON:{}")
                .setArguments(description)
                .setThrowable(e)
        );
        return new RuntimeException("Exception while serializing expectation to JSON (" + description + ")", e);
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
                    throw new IllegalArgumentException("exception while parsing [" + jsonExpectation + "] for Expectation" + rootCauseMessage(throwable), throwable);
                }
                return expectation;
            } else {
                throw new IllegalArgumentException(Strings.CI.removeEnd(formatLogMessage("incorrect expectation json format for:{}schema validation errors:{}", jsonExpectation, validationErrors), "\n"));
            }
        }
    }

    // the innermost message says what is wrong, for example why a date-time matcher value is not valid
    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return isBlank(root.getMessage()) ? "" : " because " + root.getMessage();
    }

    @Override
    public Class<Expectation> supportsType() {
        return Expectation.class;
    }

    public Expectation[] deserializeArray(String jsonExpectations, boolean allowEmpty) {
        return deserializeArray(jsonExpectations, allowEmpty, (s, expectation) -> expectation);
    }

    public Expectation[] deserializeArray(String jsonExpectations, boolean allowEmpty, BiFunction<String, List<Expectation>, List<Expectation>> expectationModifier) {
        return deserializeArray(jsonExpectations, allowEmpty, expectationModifier, null);
    }

    /**
     * Like {@link #deserializeArray(String, boolean, BiFunction)}, except that an entry which fails validation is
     * left out and passed to {@code invalidEntryHandler} (as a description naming the entry, and the validation
     * errors) instead of failing the whole array. For loading expectation files, where one bad entry should not
     * cost the rest; the control-plane API stays strict.
     */
    public Expectation[] deserializeArrayDroppingInvalidEntries(String jsonExpectations, BiFunction<String, List<Expectation>, List<Expectation>> expectationModifier, BiConsumer<String, String> invalidEntryHandler) {
        return deserializeArray(jsonExpectations, true, expectationModifier, invalidEntryHandler);
    }

    /**
     * Names an entry of an expectation array for a log message: its position, and its id and request path when
     * it has them.
     */
    static String describeEntry(JsonNode entry, int index, int count) {
        StringBuilder description = new StringBuilder("expectation ").append(index + 1).append(" of ").append(count);
        JsonNode id = entry.get("id");
        if (id != null && id.isTextual()) {
            description.append(" (id \"").append(id.asText()).append("\")");
        }
        JsonNode httpRequest = entry.get("httpRequest");
        JsonNode path = httpRequest != null ? httpRequest.get("path") : null;
        if (path != null && path.isValueNode()) {
            description.append(" for path \"").append(path.asText()).append("\"");
        }
        return description.toString();
    }

    private Expectation[] deserializeArray(String jsonExpectations, boolean allowEmpty, BiFunction<String, List<Expectation>, List<Expectation>> expectationModifier, @Nullable BiConsumer<String, String> invalidEntryHandler) {
        List<Expectation> expectations = new ArrayList<>();
        if (isBlank(jsonExpectations)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - an expectation or expectation array is required but value was \"" + jsonExpectations + "\"");
        } else {
            List<String> validationErrorsList = new ArrayList<>();
            List<JsonNode> jsonExpectationList = jsonArraySerializer.splitJSONArrayToJSONNodes(jsonExpectations);
            if (!jsonExpectationList.isEmpty()) {
                for (int i = 0; i < jsonExpectationList.size(); i++) {
                    String jsonExpectation = JsonPrettyPrinter.prettyPrint(jsonExpectationList.get(i));
                    if (jsonExpectationList.size() > 100) {
                        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(DEBUG)
                                    .setMessageFormat("processing JSON expectation " + (i + 1) + " of " + jsonExpectationList.size() + ":{}")
                                    .setArguments(jsonExpectation)
                            );
                        } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(INFO)
                                    .setMessageFormat("processing JSON expectation " + (i + 1) + " of " + jsonExpectationList.size())
                            );
                        }
                    }
                    try {
                        if (jsonExpectationList.get(i).has("specUrlOrPayload")) {
                            expectations.addAll(expectationModifier.apply(jsonExpectation, openAPIExpectationSerializer.deserializeToExpectations(jsonExpectation)));
                        } else {
                            expectations.addAll(expectationModifier.apply(jsonExpectation, Collections.singletonList(deserialize(jsonExpectation))));
                        }
                    } catch (IllegalArgumentException iae) {
                        if (invalidEntryHandler != null) {
                            invalidEntryHandler.accept(describeEntry(jsonExpectationList.get(i), i, jsonExpectationList.size()), iae.getMessage());
                        } else {
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
