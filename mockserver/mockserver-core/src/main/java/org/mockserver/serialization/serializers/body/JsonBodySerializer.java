package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper()
        .copy()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
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
        boolean matchNumbersAsStringsNonDefault = jsonBody.isMatchNumbersAsStrings();
        if (serialiseDefaultValues || notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault || matchNumbersAsStringsNonDefault) {
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
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonBody.getValue());
            if (jsonNode.isValueNode()) {
                jgen.writeFieldName("json");
                jgen.writeRawValue(jsonBody.getValue());
            } else {
                jgen.writeObjectField("json", jsonNode);
            }
            if (matchTypeNonDefault) {
                jgen.writeStringField("matchType", jsonBody.getMatchType().name());
            }
            if (matchNumbersAsStringsNonDefault) {
                jgen.writeBooleanField("matchNumbersAsStrings", true);
            }
            jgen.writeEndObject();
        } else {
            JsonNode defaultJsonNode = OBJECT_MAPPER.readTree(jsonBody.getValue());
            if (defaultJsonNode.isValueNode()) {
                jgen.writeRawValue(jsonBody.getValue());
            } else {
                jgen.writeObject(defaultJsonNode);
            }
        }
    }
}
