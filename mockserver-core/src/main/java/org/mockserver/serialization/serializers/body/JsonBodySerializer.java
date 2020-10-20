package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodySerializer extends StdSerializer<JsonBody> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonBodySerializer() {
        super(JsonBody.class);
    }

    @Override
    public void serialize(JsonBody jsonBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (jsonBody.getNot() != null && jsonBody.getNot()) {
            jgen.writeBooleanField("not", jsonBody.getNot());
        }
        if (jsonBody.getOptional() != null && jsonBody.getOptional()) {
            jgen.writeBooleanField("optional", jsonBody.getOptional());
        }
        if (jsonBody.getContentType() != null && !jsonBody.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString())) {
            jgen.writeStringField("contentType", jsonBody.getContentType());
        }
        jgen.writeStringField("type", jsonBody.getType().name());
        jgen.writeObjectField("json", OBJECT_MAPPER.readTree(jsonBody.getValue()));
        if (jsonBody.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE) {
            jgen.writeStringField("matchType", jsonBody.getMatchType().name());
        }
        jgen.writeEndObject();
    }
}
