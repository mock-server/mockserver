package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.JsonSchemaBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTOSerializer extends StdSerializer<JsonSchemaBodyDTO> {

    public JsonSchemaBodyDTOSerializer() {
        super(JsonSchemaBodyDTO.class);
    }

    @Override
    public void serialize(JsonSchemaBodyDTO jsonSchemaBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonSchemaBodyDTO.getNot() != null && jsonSchemaBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonSchemaBodyDTO.getNot());
        }
        jgen.writeStringField("type", jsonSchemaBodyDTO.getType().name());
        jgen.writeStringField("jsonSchema", jsonSchemaBodyDTO.getJson());
        jgen.writeEndObject();
    }
}
