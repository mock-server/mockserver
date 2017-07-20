package org.mockserver.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.file.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaHttpRequestValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
            "org/mockserver/model/schema/",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue"
    );

    public JsonSchemaHttpRequestValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
