package org.mockserver.client.serialization.serializers;

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
    public void serialize(JsonSchemaBody jsonSchemaBody, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        if (jsonSchemaBody.isNot() != null && jsonSchemaBody.isNot()) {
            json.writeBooleanField("not", jsonSchemaBody.isNot());
        }
        json.writeStringField("type", jsonSchemaBody.getType().name());
        json.writeStringField("value", jsonSchemaBody.getValue());
        json.writeEndObject();
    }
}
