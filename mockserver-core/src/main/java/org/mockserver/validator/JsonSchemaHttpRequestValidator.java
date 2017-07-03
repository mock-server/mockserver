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
    private static String jsonSchema = "";

    static {
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            final JsonNode jsonSchema = objectMapper.readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/model/schema/httpRequest.json"));
            final JsonNode definitions = jsonSchema.get("definitions");
            List<String> definitionNames = Arrays.asList(
                    "body",
                    "keyToMultiValue",
                    "keyToValue"
            );
            for (String definitionName : definitionNames) {
                if (definitions != null && definitions instanceof ObjectNode) {
                    ((ObjectNode) definitions).set(definitionName, objectMapper.readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/model/schema/" + definitionName + ".json")));
                }
            }
            JsonSchemaHttpRequestValidator.jsonSchema = ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(jsonSchema);
        } catch (Exception e) {
            logger.error("Exception loading JSON Schema for Exceptions", e);
        }
    }

    public JsonSchemaHttpRequestValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
