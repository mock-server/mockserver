package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

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

        if (matcher.equalsIgnoreCase(matched)) {
            result = true;
        } else if (!StringUtils.isBlank(matched)) {
            try {
                String validation = jsonSchemaValidator.isValid(matched, false);

                result = validation.isEmpty();

                if (!result && context != null) {
                    context.addDifference(mockServerLogger, "json schema match failed expected:{}found:{}failed because:{}", this.matcher, matched, validation);
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "json schema match failed expected:{}found:{}failed because:{}", this.matcher, matched, throwable.getMessage());
                }
            }
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
