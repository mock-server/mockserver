package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodySerializer extends StdSerializer<JsonSchemaBody> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaBodySerializer() {
        super(JsonSchemaBody.class);
    }

    @Override
    public void serialize(JsonSchemaBody jsonSchemaBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonSchemaBody.getNot() != null && jsonSchemaBody.getNot()) {
            jgen.writeBooleanField("not", jsonSchemaBody.getNot());
        }
        if (jsonSchemaBody.getOptional() != null && jsonSchemaBody.getOptional()) {
            jgen.writeBooleanField("optional", jsonSchemaBody.getOptional());
        }
        jgen.writeStringField("type", jsonSchemaBody.getType().name());
        jgen.writeObjectField("jsonSchema", OBJECT_MAPPER.readTree(jsonSchemaBody.getValue()));
        if (jsonSchemaBody.getParameterStyles() != null) {
            jgen.writeObjectField("parameterStyles", jsonSchemaBody.getParameterStyles());
        }
        jgen.writeEndObject();
    }
}
