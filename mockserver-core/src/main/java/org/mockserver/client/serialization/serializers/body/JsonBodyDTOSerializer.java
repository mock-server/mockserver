package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.JsonBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOSerializer extends StdSerializer<JsonBodyDTO> {

    public JsonBodyDTOSerializer() {
        super(JsonBodyDTO.class);
    }

    @Override
    public void serialize(JsonBodyDTO jsonBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonBodyDTO.getNot());
        }
        jgen.writeStringField("type", jsonBodyDTO.getType().name());
        jgen.writeStringField("json", jsonBodyDTO.getJson());
        jgen.writeEndObject();
    }
}
