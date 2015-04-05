package org.mockserver.client.serialization.serializers.body;

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
    public void serialize(JsonBody jsonBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonBody.isNot()) {
            jgen.writeBooleanField("not", jsonBody.isNot());
        }
        jgen.writeStringField("type", jsonBody.getType().name());
        jgen.writeStringField("json", jsonBody.getValue());
        jgen.writeEndObject();
    }
}
