package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.JsonBodyDTO;
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
    public void serialize(JsonSchemaBodyDTO jsonSchemaBodyDTO, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        json.writeStringField("type", jsonSchemaBodyDTO.getType().name());
        json.writeStringField("value", jsonSchemaBodyDTO.getJson());
        json.writeEndObject();
    }
}
