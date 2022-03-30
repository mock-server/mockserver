package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;

/**
 * @author jamesdbloom
 */
public class JsonBodySerializer extends StdSerializer<JsonBody> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private final boolean serialiseDefaultValues;

    public JsonBodySerializer(boolean serialiseDefaultValues) {
        super(JsonBody.class);
        this.serialiseDefaultValues = serialiseDefaultValues;
    }

    @Override
    public void serialize(JsonBody jsonBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        boolean notNonDefault = jsonBody.getNot() != null && jsonBody.getNot();
        boolean optionalNonDefault = jsonBody.getOptional() != null && jsonBody.getOptional();
        boolean contentTypeNonDefault = jsonBody.getContentType() != null && !jsonBody.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString());
        boolean matchTypeNonDefault = jsonBody.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE;
        if (serialiseDefaultValues || notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault) {
            jgen.writeStartObject();
            if (notNonDefault) {
                jgen.writeBooleanField("not", jsonBody.getNot());
            }
            if (optionalNonDefault) {
                jgen.writeBooleanField("optional", jsonBody.getOptional());
            }
            if (contentTypeNonDefault) {
                jgen.writeStringField("contentType", jsonBody.getContentType());
            }
            jgen.writeStringField("type", jsonBody.getType().name());
            try {
                jgen.writeObjectField("json", OBJECT_MAPPER.readTree(jsonBody.getValue()));
            } catch (Throwable throwable) {
                new MockServerLogger().logEvent(
                    new LogEntry()
                        .setType(EXCEPTION)
                        .setMessageFormat("exception:{} while deserialising jsonBody with json:{}")
                        .setArguments(throwable.getMessage(), jsonBody.getValue())
                        .setThrowable(throwable)
                );
            }
            if (matchTypeNonDefault) {
                jgen.writeStringField("matchType", jsonBody.getMatchType().name());
            }
            jgen.writeEndObject();
        } else {
            jgen.writeObject(OBJECT_MAPPER.readTree(jsonBody.getValue()));
        }
    }
}
