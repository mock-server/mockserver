package org.mockserver.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Override
    public List<String> isValid(String s) {
        return null;
    }

    public String validateJson(String schema, String json) {
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
