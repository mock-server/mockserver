package org.mockserver.serialization.deserializers.string;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.NOT_CHAR;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializer extends StdDeserializer<NottableString> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public NottableStringDeserializer() {
        super(NottableString.class);
    }

    @Override
    public NottableString deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        String nottableValue = null;
        JsonNode potentiallyJsonField = null;
        Boolean not = null;
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            potentiallyJsonField = ctxt.readValue(jsonParser, JsonNode.class);
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
            nottableValue = ctxt.readValue(jsonParser, String.class);
            if (isNotBlank(nottableValue)) {
                try {
                    String potentialJsonString;
                    if (nottableValue.charAt(0) == NOT_CHAR) {
                        not = true;
                        potentialJsonString = nottableValue.substring(1);
                    } else {
                        potentialJsonString = nottableValue;
                    }
                    if (potentialJsonString.matches("^\\s*[{\\[]{1}")) {
                        potentiallyJsonField = OBJECT_MAPPER.readTree(potentialJsonString);
                    }
                } catch (Throwable throwable) {
                    return string(nottableValue);
                }
            }
        }
        if (potentiallyJsonField != null) {
            if (potentiallyJsonField.isTextual()) {
                return string(potentiallyJsonField.asText(), not);
            } else {
                return schemaString(potentiallyJsonField.toPrettyString(), not);
            }
        } else if (nottableValue != null) {
            return string(nottableValue);
        } else {
            return null;
        }
    }

}
