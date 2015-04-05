package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.StringBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class StringBodyDTOSerializer extends StdSerializer<StringBodyDTO> {

    public StringBodyDTOSerializer() {
        super(StringBodyDTO.class);
    }

    @Override
    public void serialize(StringBodyDTO stringBodyDTO, JsonGenerator json, SerializerProvider provider) throws IOException {
        if (stringBodyDTO.getNot() != null && stringBodyDTO.getNot()) {
            json.writeStartObject();
            json.writeBooleanField("not", true);
            json.writeStringField("type", stringBodyDTO.getType().name());
            json.writeStringField("value", stringBodyDTO.getString());
            json.writeEndObject();
        } else {
            json.writeString(stringBodyDTO.getString());
        }
    }
}
