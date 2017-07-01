package org.mockserver.matchers;

import org.mockserver.validator.JsonSchemaValidator;

/**
 * See http://json-schema.org/
 *
 * @author jamesdbloom
 */
public class JsonSchemaMatcher extends BodyMatcher<String> {
    private final String schema;
    private JsonSchemaValidator jsonSchemaValidator;

    public JsonSchemaMatcher(String schema) {
        this.schema = schema;
        jsonSchemaValidator = new JsonSchemaValidator(schema);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "objectMapper"};
    }

    public boolean matches(String matched) {
        boolean result = false;

        try {
            String validation = jsonSchemaValidator.isValid(matched);

            result = validation.isEmpty();

            if (!result) {
                logger.trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", matched, this.schema, validation);
            }
        } catch (Exception e) {
            logger.trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", matched, this.schema, e.getMessage());
        }

        return reverseResultIfNot(result);
    }

}
