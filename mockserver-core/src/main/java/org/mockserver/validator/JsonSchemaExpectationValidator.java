package org.mockserver.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.file.FileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaExpectationValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
                "org/mockserver/model/schema/",
                "expectation",
                "httpRequest",
                "httpResponse",
                "httpForward",
                "httpClassCallback",
                "httpObjectCallback",
                "httpError",
                "times",
                "timeToLive",
                "body",
                "bodyWithContentType",
                "delay",
                "connectionOptions",
                "keyToMultiValue",
                "keyToValue"
    );

    public JsonSchemaExpectationValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
