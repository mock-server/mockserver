package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

import static org.slf4j.event.Level.DEBUG;

/**
 * See http://json-schema.org/
 *
 * @author jamesdbloom
 */
public class JsonSchemaMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "jsonSchemaValidator"};
    private final MockServerLogger mockServerLogger;
    private String matcher;
    private JsonSchemaValidator jsonSchemaValidator;

    JsonSchemaMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        jsonSchemaValidator = new JsonSchemaValidator(mockServerLogger, matcher);
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        try {
            String validation = jsonSchemaValidator.isValid(matched, false);

            result = validation.isEmpty();

            if (!result) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMatchDifference(context)
                        .setMessageFormat("json schema match failed expected:{}found:{}failed because:{}")
                        .setArguments(this.matcher, matched, validation)
                );
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("json schema match failed expected:{}found:{}failed because:{}")
                    .setArguments(this.matcher, matched, e.getMessage())
            );
        }

        return not != result;
    }

    public boolean isBlank() {
        return StringUtils.isBlank(matcher);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

}
