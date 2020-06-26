package org.mockserver.serialization.deserializers.string;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.OPTIONAL_CHAR;
import static org.mockserver.model.NottableOptionalString.optionalString;
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
        String potentialJsonString = null;
        Boolean not = null;
        boolean optional = false;
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            potentiallyJsonField = ctxt.readValue(jsonParser, JsonNode.class);
            if (potentiallyJsonField.has("not")) {
                not = potentiallyJsonField.get("not").asBoolean(false);
                if (potentiallyJsonField instanceof ObjectNode) {
                    ((ObjectNode) potentiallyJsonField).remove("not");
                }
            }
            potentialJsonString = potentiallyJsonField.toPrettyString();
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
            nottableValue = ctxt.readValue(jsonParser, String.class);
            if (isNotBlank(nottableValue)) {
                try {
                    if (nottableValue.charAt(0) == NOT_CHAR) {
                        not = true;
                        nottableValue = nottableValue.substring(1);
                        potentialJsonString = nottableValue;
                    } else if (nottableValue.charAt(0) == OPTIONAL_CHAR) {
                        optional = true;
                        nottableValue = nottableValue.substring(1);
                        potentialJsonString = nottableValue;
                    } else {
                        potentialJsonString = nottableValue;
                    }
                    if (potentialJsonString.matches("^\\s*[{\\[]{1}")) {
                        potentiallyJsonField = OBJECT_MAPPER.readTree(potentialJsonString);
                        if (potentiallyJsonField.has("not")) {
                            not = potentiallyJsonField.get("not").asBoolean(false);
                            if (potentiallyJsonField instanceof ObjectNode) {
                                ((ObjectNode) potentiallyJsonField).remove("not");
                            }
                            potentialJsonString = potentiallyJsonField.toPrettyString();
                        }
                    }
                } catch (Throwable throwable) {
                    return string(nottableValue);
                }
            }
        }
        if (isNotBlank(potentialJsonString) && potentiallyJsonField != null) {
            if (potentiallyJsonField.isTextual()) {
                if (optional) {
                    return optionalString(potentialJsonString, false);
                } else {
                    return string(potentialJsonString, not);
                }
            } else {
                return schemaString(potentialJsonString, not);
            }
        } else if (nottableValue != null) {
            if (optional) {
                return optionalString(nottableValue, not);
            } else {
                return string(nottableValue, not);
            }
        } else {
            return null;
        }
    }

}
