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
    public NottableString deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
            Boolean not = null;
            String string = null;

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                if ("not".equals(fieldName)) {
                    jp.nextToken();
                    not = jp.getBooleanValue();
                } else if ("value".equals(fieldName)) {
                    jp.nextToken();
                    string = jp.readValueAs(String.class);
                }
            }

            if (StringUtils.isEmpty(string)) {
                return null;
            }

            return string(string, not);
        } else if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
            return string(jp.readValueAs(String.class));
        }
        return null;
    }

}
