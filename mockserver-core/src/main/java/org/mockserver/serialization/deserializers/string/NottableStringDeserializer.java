package org.mockserver.serialization.deserializers.string;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.model.NottableString;
import org.mockserver.model.ParameterStyle;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
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
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            Boolean not = null;
            Boolean optional = null;
            String value = null;
            JsonNode schema = null;
            ParameterStyle parameterStyle = null;

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.getCurrentName();
                if ("not".equals(fieldName)) {
                    jsonParser.nextToken();
                    not = jsonParser.getBooleanValue();
                } else if ("optional".equals(fieldName)) {
                    jsonParser.nextToken();
                    optional = jsonParser.getBooleanValue();
                } else if ("value".equals(fieldName)) {
                    jsonParser.nextToken();
                    value = ctxt.readValue(jsonParser, String.class);
                } else if ("schema".equals(fieldName)) {
                    jsonParser.nextToken();
                    schema = ctxt.readValue(jsonParser, JsonNode.class);
                } else if ("parameterStyle".equals(fieldName)) {
                    jsonParser.nextToken();
                    parameterStyle = ctxt.readValue(jsonParser, ParameterStyle.class);
                }
            }

            NottableString result = null;
            if (schema != null) {
                result = schemaString(schema.toPrettyString(), not);
            } else if (Boolean.TRUE.equals(optional)) {
                result = optional(value, not);
            } else if (isNotBlank(value)) {
                result = string(value, not);
            }

            if (result != null && parameterStyle != null) {
                result.withStyle(parameterStyle);
            }

            return result;
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
            return string(ctxt.readValue(jsonParser, String.class));
        }
        return null;
    }

}
