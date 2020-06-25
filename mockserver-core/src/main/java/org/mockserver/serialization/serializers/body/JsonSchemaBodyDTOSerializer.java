package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonSchemaBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTOSerializer extends StdSerializer<JsonSchemaBodyDTO> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaBodyDTOSerializer() {
        super(JsonSchemaBodyDTO.class);
    }

    @Override
    public void serialize(JsonSchemaBodyDTO jsonSchemaBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonSchemaBodyDTO.getNot() != null && jsonSchemaBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonSchemaBodyDTO.getNot());
        }
        if (jsonSchemaBodyDTO.getOptional() != null && jsonSchemaBodyDTO.getOptional()) {
            jgen.writeBooleanField("optional", jsonSchemaBodyDTO.getOptional());
        }
        jgen.writeStringField("type", jsonSchemaBodyDTO.getType().name());
        jgen.writeObjectField("jsonSchema", OBJECT_MAPPER.readTree(jsonSchemaBodyDTO.getJson()));
        jgen.writeEndObject();
    }
}
