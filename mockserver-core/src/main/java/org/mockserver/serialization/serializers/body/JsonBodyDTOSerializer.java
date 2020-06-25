package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonBodyDTO;
import org.mockserver.model.JsonBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOSerializer extends StdSerializer<JsonBodyDTO> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonBodyDTOSerializer() {
        super(JsonBodyDTO.class);
    }

    @Override
    public void serialize(JsonBodyDTO jsonBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonBodyDTO.getNot());
        }
        if (jsonBodyDTO.getOptional() != null && jsonBodyDTO.getOptional()) {
            jgen.writeBooleanField("optional", jsonBodyDTO.getOptional());
        }
        if (jsonBodyDTO.getContentType() != null && !jsonBodyDTO.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString())) {
            jgen.writeStringField("contentType", jsonBodyDTO.getContentType());
        }
        jgen.writeStringField("type", jsonBodyDTO.getType().name());
        jgen.writeObjectField("json", OBJECT_MAPPER.readTree(jsonBodyDTO.getJson()));
        if (jsonBodyDTO.getRawBytes() != null) {
            jgen.writeObjectField("rawBytes", jsonBodyDTO.getRawBytes());
        }
        if (jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE) {
            jgen.writeStringField("matchType", jsonBodyDTO.getMatchType().name());
        }
        jgen.writeEndObject();
    }
}
