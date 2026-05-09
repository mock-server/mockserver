package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.JsonBody;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOSerializer extends StdSerializer<JsonBodyDTO> {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper()
        .copy()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final boolean serialiseDefaultValues;

    public JsonBodyDTOSerializer(boolean serialiseDefaultValues) {
        super(JsonBodyDTO.class);
        this.serialiseDefaultValues = serialiseDefaultValues;
    }

    @Override
    public void serialize(JsonBodyDTO jsonBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        boolean notNonDefault = jsonBodyDTO.getNot() != null && jsonBodyDTO.getNot();
        boolean optionalNonDefault = jsonBodyDTO.getOptional() != null && jsonBodyDTO.getOptional();
        boolean contentTypeNonDefault = jsonBodyDTO.getContentType() != null && !jsonBodyDTO.getContentType().equals(JsonBody.DEFAULT_JSON_CONTENT_TYPE.toString());
        boolean matchTypeNonDefault = jsonBodyDTO.getMatchType() != JsonBody.DEFAULT_MATCH_TYPE;
        boolean matchNumbersAsStringsNonDefault = jsonBodyDTO.isMatchNumbersAsStrings();
        if (serialiseDefaultValues || notNonDefault || optionalNonDefault || contentTypeNonDefault || matchTypeNonDefault || matchNumbersAsStringsNonDefault) {
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
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonBodyDTO.getJson());
            if (jsonNode.isValueNode()) {
                jgen.writeFieldName("json");
                jgen.writeRawValue(jsonBodyDTO.getJson());
            } else {
                jgen.writeObjectField("json", jsonNode);
            }
            if (jsonBodyDTO.getRawBytes() != null) {
                jgen.writeObjectField("rawBytes", jsonBodyDTO.getRawBytes());
            }
            if (matchTypeNonDefault) {
                jgen.writeStringField("matchType", jsonBodyDTO.getMatchType().name());
            }
            if (matchNumbersAsStringsNonDefault) {
                jgen.writeBooleanField("matchNumbersAsStrings", true);
            }
            jgen.writeEndObject();
        } else {
            JsonNode defaultJsonNode = OBJECT_MAPPER.readTree(jsonBodyDTO.getJson());
            if (defaultJsonNode.isValueNode()) {
                jgen.writeRawValue(jsonBodyDTO.getJson());
            } else {
                jgen.writeObject(defaultJsonNode);
            }
        }
    }
}
