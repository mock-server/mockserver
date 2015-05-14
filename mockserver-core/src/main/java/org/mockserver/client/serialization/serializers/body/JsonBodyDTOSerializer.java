package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.JsonBodyDTO;
import org.mockserver.mappers.ContentTypeMapper;
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
        if (jsonBodyDTO.getCharset() != null && !jsonBodyDTO.getCharset().equals(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)) {
            jgen.writeStringField("charset", jsonBodyDTO.getCharset().name());
        }
        jgen.writeStringField("type", jsonBodyDTO.getType().name());
        jgen.writeStringField("json", jsonBodyDTO.getJson());
        if (jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE) {
            jgen.writeStringField("matchType", jsonBodyDTO.getMatchType().name());
        }
        jgen.writeEndObject();
    }
}
