package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonSchemaBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodySerializer extends StdSerializer<JsonSchemaBody> {

    public JsonSchemaBodySerializer() {
        super(JsonSchemaBody.class);
    }

    @Override
    public void serialize(JsonSchemaBody jsonSchemaBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonSchemaBody.isNot()) {
            jgen.writeBooleanField("not", jsonSchemaBody.isNot());
        }
        jgen.writeStringField("type", jsonSchemaBody.getType().name());
        jgen.writeStringField("jsonSchema", jsonSchemaBody.getValue());
        jgen.writeEndObject();
    }
}
