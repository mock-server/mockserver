package org.mockserver.client.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.BinaryBodyDTO;
import org.mockserver.client.serialization.model.BodyDTO;
import org.mockserver.client.serialization.model.ParameterBodyDTO;
import org.mockserver.client.serialization.model.StringBodyDTO;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactory {

    public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // ignore failures
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // relax parsing
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

        // use arrays
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);

        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        // register our own module with our serializers and deserializers
        Module gameServerModule = new Module();
        objectMapper.registerModule(gameServerModule);

        return objectMapper;
    }

    private static class Module extends SimpleModule {

        public Module() {
            addDeserializer(BodyDTO.class, new BodyDTODeserializer());
            addSerializer(StringBodyDTO.class, new StringBodyDTOSerializer());
            addSerializer(StringBody.class, new StringBodySerializer());
        }

    }

    private static class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

        protected BodyDTODeserializer() {
            super(BodyDTO.class);
        }

        @Override
        public BodyDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
            JsonToken currentToken = jsonParser.getCurrentToken();
            if (currentToken == JsonToken.START_OBJECT) {
                jsonParser.nextToken();
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equals("type")) {
                    jsonParser.nextToken();
                    if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                        Body.Type type = Body.Type.valueOf(jsonParser.getText());
                        jsonParser.nextToken();
                        switch (type) {
                            case STRING:
                            case REGEX:
                            case JSON:
                            case XPATH:
                                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equals("value")) {
                                    jsonParser.nextToken();
                                    if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                                        String value = jsonParser.getText();
                                        jsonParser.nextToken();
                                        if (jsonParser.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new StringBodyDTO(new StringBody(value, type));
                                        }
                                    }
                                }
                                break;
                            case BINARY:
                                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equals("value")) {
                                    jsonParser.nextToken();
                                    if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                                        String value = jsonParser.getText();
                                        jsonParser.nextToken();
                                        if (jsonParser.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new BinaryBodyDTO(new BinaryBody(Base64Converter.base64StringToBytes(value)));
                                        }
                                    }
                                }
                                break;
                            case PARAMETERS:
                                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equals("parameters")) {
                                    jsonParser.nextToken();
                                    if (jsonParser.isExpectedStartArrayToken()) {
                                        List<Parameter> parameters = new ArrayList<Parameter>();
                                        boolean inObject = false;
                                        while (inObject || jsonParser.getCurrentToken() != JsonToken.END_ARRAY) {
                                            JsonToken token = jsonParser.nextToken();
                                            switch (token) {
                                                case START_OBJECT:
                                                    inObject = true;
                                                    break;
                                                case END_OBJECT:
                                                    inObject = false;
                                                    break;
                                                case FIELD_NAME:
                                                    if (jsonParser.getText().equals("name")) {
                                                        if (jsonParser.nextToken() == JsonToken.VALUE_STRING) {
                                                            String name = jsonParser.getText();
                                                            jsonParser.nextToken();
                                                            if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
                                                                List<String> values = new ArrayList<String>();
                                                                while (jsonParser.nextToken() != null && jsonParser.getCurrentToken() != JsonToken.END_ARRAY) {
                                                                    if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                                                                        values.add(jsonParser.getText());
                                                                    }
                                                                }
                                                                parameters.add(new Parameter(name, values));
                                                            }
                                                        }
                                                    }
                                                    break;
                                            }
                                        }
                                        jsonParser.nextToken();
                                        if (jsonParser.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new ParameterBodyDTO(new ParameterBody(parameters));
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
            } else if (currentToken == JsonToken.VALUE_STRING) {
                return new StringBodyDTO(new StringBody(jsonParser.getText(), Body.Type.STRING));
            }
            return null;
        }
    }

    private static class StringBodyDTOSerializer extends StdSerializer<StringBodyDTO> {

        protected StringBodyDTOSerializer() {
            super(StringBodyDTO.class);
        }

        @Override
        public void serialize(StringBodyDTO stringBodyDTO, JsonGenerator json, SerializerProvider provider) throws IOException {
            if (stringBodyDTO.getType() == Body.Type.STRING) {
                json.writeString(stringBodyDTO.getValue());
            } else {
                json.writeStartObject();
                json.writeStringField("type", stringBodyDTO.getType().name());
                json.writeStringField("value", stringBodyDTO.getValue());
                json.writeEndObject();
            }
        }
    }

    private static class StringBodySerializer extends StdSerializer<StringBody> {

        protected StringBodySerializer() {
            super(StringBody.class);
        }

        @Override
        public void serialize(StringBody stringBody, JsonGenerator json, SerializerProvider provider) throws IOException {
            if (stringBody.getType() == Body.Type.STRING) {
                json.writeString(stringBody.getValue());
            } else {
                json.writeStartObject();
                json.writeStringField("type", stringBody.getType().name());
                json.writeStringField("value", stringBody.getValue());
                json.writeEndObject();
            }
        }
    }
}