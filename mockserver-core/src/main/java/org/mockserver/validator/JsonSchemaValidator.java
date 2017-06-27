package org.mockserver.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    private final String schema;
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaValidator(String schema) {
        this.schema = schema;
    }

    @Override
    public String isValid(String json) {
        try {
            final ProcessingReport validate = JsonSchemaFactory
                    .byDefault()
                    .getValidator()
                    .validate(objectMapper.readTree(schema), objectMapper.readTree(json), true);

            if (validate.isSuccess()) {
                return "";
            } else {
                return validate.toString();
            }
        } catch (Exception e) {
            logger.info("Exception validating JSON", e);
            return e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}
