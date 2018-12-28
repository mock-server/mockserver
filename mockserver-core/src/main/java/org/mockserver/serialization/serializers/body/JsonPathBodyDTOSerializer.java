package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.JsonPathBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonPathBodyDTOSerializer extends StdSerializer<JsonPathBodyDTO> {

    public JsonPathBodyDTOSerializer() {
        super(JsonPathBodyDTO.class);
    }

    @Override
    public void serialize(JsonPathBodyDTO jsonPathBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonPathBodyDTO.getNot() != null && jsonPathBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonPathBodyDTO.getNot());
        }
        jgen.writeStringField("type", jsonPathBodyDTO.getType().name());
        jgen.writeStringField("jsonPath", jsonPathBodyDTO.getJsonPath());
        jgen.writeEndObject();
    }
}
