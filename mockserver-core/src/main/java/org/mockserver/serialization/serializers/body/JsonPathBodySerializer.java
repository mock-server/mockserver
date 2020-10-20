package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonPathBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonPathBodySerializer extends StdSerializer<JsonPathBody> {

    public JsonPathBodySerializer() {
        super(JsonPathBody.class);
    }

    @Override
    public void serialize(JsonPathBody jsonPathBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonPathBody.getNot() != null && jsonPathBody.getNot()) {
            jgen.writeBooleanField("not", jsonPathBody.getNot());
        }
        if (jsonPathBody.getOptional() != null && jsonPathBody.getOptional()) {
            jgen.writeBooleanField("optional", jsonPathBody.getOptional());
        }
        jgen.writeStringField("type", jsonPathBody.getType().name());
        jgen.writeStringField("jsonPath", jsonPathBody.getValue());
        jgen.writeEndObject();
    }
}
