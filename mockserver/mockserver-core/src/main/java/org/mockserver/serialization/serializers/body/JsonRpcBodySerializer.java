package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonRpcBody;

import java.io.IOException;

public class JsonRpcBodySerializer extends StdSerializer<JsonRpcBody> {

    public JsonRpcBodySerializer() {
        super(JsonRpcBody.class);
    }

    @Override
    public void serialize(JsonRpcBody jsonRpcBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonRpcBody.getNot() != null && jsonRpcBody.getNot()) {
            jgen.writeBooleanField("not", jsonRpcBody.getNot());
        }
        if (jsonRpcBody.getOptional() != null && jsonRpcBody.getOptional()) {
            jgen.writeBooleanField("optional", jsonRpcBody.getOptional());
        }
        jgen.writeStringField("type", jsonRpcBody.getType().name());
        jgen.writeStringField("method", jsonRpcBody.getMethod());
        if (jsonRpcBody.getParamsSchema() != null) {
            jgen.writeStringField("paramsSchema", jsonRpcBody.getParamsSchema());
        }
        jgen.writeEndObject();
    }
}
