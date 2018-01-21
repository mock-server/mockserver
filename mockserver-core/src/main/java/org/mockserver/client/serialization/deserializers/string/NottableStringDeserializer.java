package org.mockserver.client.serialization.deserializers.string;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializer extends JsonDeserializer<NottableString> {

    @Override
    public NottableString deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            Boolean not = null;
            String string = null;

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.getCurrentName();
                if ("not".equals(fieldName)) {
                    jsonParser.nextToken();
                    not = jsonParser.getBooleanValue();
                } else if ("value".equals(fieldName)) {
                    jsonParser.nextToken();
                    string = ctxt.readValue(jsonParser, String.class);
                }
            }

            if (StringUtils.isEmpty(string)) {
                return null;
            }

            return string(string, not);
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
            return string(ctxt.readValue(jsonParser, String.class));
        }
        return null;
    }

}
