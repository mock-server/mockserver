package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * See http://json-schema.org/
 *
 * @author jamesdbloom
 */
public class JsonSchemaMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"key", "mockServerLogger", "jsonSchemaValidator"};
    private final MockServerLogger mockServerLogger;
    private String schema;
    private JsonSchemaValidator jsonSchemaValidator;

    JsonSchemaMatcher(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        this.schema = schema;
        jsonSchemaValidator = new JsonSchemaValidator(mockServerLogger, schema);
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        try {
            String validation = jsonSchemaValidator.isValid(matched);

            result = validation.isEmpty();

            if (!result) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(DEBUG)
                        .setHttpRequest(context)
                        .setMessageFormat("Failed to perform json schema match of {} with {} because {}")
                        .setArguments(matched, this.schema, validation)
                );
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("Failed to perform json schema match of {} with {} because {}")
                    .setArguments(matched, this.schema, e.getMessage())
            );
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

}
