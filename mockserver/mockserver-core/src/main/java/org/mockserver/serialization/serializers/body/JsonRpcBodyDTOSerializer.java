package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.JsonRpcBodyDTO;

import java.io.IOException;

public class JsonRpcBodyDTOSerializer extends StdSerializer<JsonRpcBodyDTO> {

    private static final long serialVersionUID = 1L;

    public JsonRpcBodyDTOSerializer() {
        super(JsonRpcBodyDTO.class);
    }

    @Override
    public void serialize(JsonRpcBodyDTO jsonRpcBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonRpcBodyDTO.getNot() != null && jsonRpcBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonRpcBodyDTO.getNot());
        }
        if (jsonRpcBodyDTO.getOptional() != null && jsonRpcBodyDTO.getOptional()) {
            jgen.writeBooleanField("optional", jsonRpcBodyDTO.getOptional());
        }
        jgen.writeStringField("type", jsonRpcBodyDTO.getType().name());
        jgen.writeStringField("method", jsonRpcBodyDTO.getMethod());
        if (jsonRpcBodyDTO.getParamsSchema() != null) {
            jgen.writeStringField("paramsSchema", jsonRpcBodyDTO.getParamsSchema());
        }
        jgen.writeEndObject();
    }
}
