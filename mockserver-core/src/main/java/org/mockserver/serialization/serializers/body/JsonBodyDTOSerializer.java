package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonBodyDTO;

import java.io.IOException;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;

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
        boolean notNonDefault = jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot();
        boolean optionalNonDefault = jsonBodyDTO.getOptional() != null && jsonBodyDTO.getOptional();
        boolean contentTypeNonDefault = jsonBodyDTO.getContentType() != null && !jsonBodyDTO.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString());
        boolean matchTypeNonDefault = jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE;
        if (notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault) {
            jgen.writeStartObject();
            if (notNonDefault) {
                jgen.writeBooleanField("not", jsonBodyDTO.getNot());
            }
            if (optionalNonDefault) {
                jgen.writeBooleanField("optional", jsonBodyDTO.getOptional());
            }
            if (contentTypeNonDefault) {
                jgen.writeStringField("contentType", jsonBodyDTO.getContentType());
            }
            jgen.writeStringField("type", jsonBodyDTO.getType().name());
            try {
                jgen.writeObjectField("json", OBJECT_MAPPER.readTree(jsonBodyDTO.getJson()));
            } catch (Throwable throwable) {
                new MockServerLogger().logEvent(
                    new LogEntry()
                        .setType(EXCEPTION)
                        .setMessageFormat("exception:{} while deserialising JsonBodyDTO with json:{}")
                        .setArguments(throwable.getMessage(), jsonBodyDTO.getJson())
                        .setThrowable(throwable)
                );
            }
            if (jsonBodyDTO.getRawBytes() != null) {
                jgen.writeObjectField("rawBytes", jsonBodyDTO.getRawBytes());
            }
            if (matchTypeNonDefault) {
                jgen.writeStringField("matchType", jsonBodyDTO.getMatchType().name());
            }
            jgen.writeEndObject();
        } else {
            jgen.writeObject(OBJECT_MAPPER.readTree(jsonBodyDTO.getJson()));
        }
    }
}
