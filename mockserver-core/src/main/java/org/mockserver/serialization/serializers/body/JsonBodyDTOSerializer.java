package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.JsonBodyDTO;
import org.mockserver.model.JsonBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOSerializer extends StdSerializer<JsonBodyDTO> {

    public JsonBodyDTOSerializer() {
        super(JsonBodyDTO.class);
    }

    @Override
    public void serialize(JsonBodyDTO jsonBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot()) {
            jgen.writeBooleanField("not", jsonBodyDTO.getNot());
        }
        if (jsonBodyDTO.getContentType() != null && !jsonBodyDTO.getContentType().equals(JsonBody.DEFAULT_CONTENT_TYPE.toString())) {
            jgen.writeStringField("contentType", jsonBodyDTO.getContentType());
        }
        jgen.writeStringField("type", jsonBodyDTO.getType().name());
        jgen.writeStringField("json", jsonBodyDTO.getJson());
        if (jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE) {
            jgen.writeStringField("matchType", jsonBodyDTO.getMatchType().name());
        }
        jgen.writeEndObject();
    }
}
