package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodySerializer extends StdSerializer<JsonBody> {

    public JsonBodySerializer() {
        super(JsonBody.class);
    }

    @Override
    public void serialize(JsonBody jsonBody, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        json.writeStringField("type", jsonBody.getType().name());
        json.writeStringField("value", jsonBody.getValue());
        json.writeEndObject();
    }
}
