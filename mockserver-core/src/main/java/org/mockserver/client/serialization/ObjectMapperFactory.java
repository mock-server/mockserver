package org.mockserver.client.serialization;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.deser.std.StdDeserializer;
import org.codehaus.jackson.map.module.SimpleModule;
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
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        objectMapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, false);
        // relax parsing
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        // use arrays
        objectMapper.configure(DeserializationConfig.Feature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY);

        SimpleModule testModule = new SimpleModule("MyModule", new Version(1, 0, 0, null));
        testModule.addDeserializer(BodyDTO.class, new BodyDTODeserializer());
        objectMapper.registerModule(testModule);
        return objectMapper;
    }

    private static class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

        protected BodyDTODeserializer() {
            super(BodyDTO.class);
        }

        @Override
        public BodyDTO deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonToken t = jp.getCurrentToken();
            if (t == JsonToken.START_OBJECT) {
                jp.nextToken();
                if (jp.getCurrentToken() == JsonToken.FIELD_NAME && jp.getText().equals("type")) {
                    jp.nextToken();
                    if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                        Body.Type type = Body.Type.valueOf(jp.getText());
                        jp.nextToken();
                        switch (type) {
                            case EXACT:
                            case REGEX:
                            case XPATH:
                                if (jp.getCurrentToken() == JsonToken.FIELD_NAME && jp.getText().equals("value")) {
                                    jp.nextToken();
                                    if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                                        String value = jp.getText();
                                        jp.nextToken();
                                        if (jp.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new StringBodyDTO(new StringBody(value, type));
                                        }
                                    }
                                }
                                break;
                            case BINARY:
                                if (jp.getCurrentToken() == JsonToken.FIELD_NAME && jp.getText().equals("value")) {
                                    jp.nextToken();
                                    if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                                        String value = jp.getText();
                                        jp.nextToken();
                                        if (jp.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new BinaryBodyDTO(new BinaryBody(value.getBytes()));
                                        }
                                    }
                                }
                                break;
                            case PARAMETERS:
                                if (jp.getCurrentToken() == JsonToken.FIELD_NAME && jp.getText().equals("parameters")) {
                                    jp.nextToken();
                                    if (jp.isExpectedStartArrayToken()) {
                                        List<Parameter> parameters = new ArrayList<Parameter>();
                                        boolean inObject = false;
                                        while (inObject || jp.getCurrentToken() != JsonToken.END_ARRAY) {
                                            JsonToken token = jp.nextToken();
                                            switch (token) {
                                                case START_OBJECT:
                                                    inObject = true;
                                                    break;
                                                case END_OBJECT:
                                                    inObject = false;
                                                    break;
                                                case FIELD_NAME:
                                                    if (jp.getText().equals("name")) {
                                                        if (jp.nextToken() == JsonToken.VALUE_STRING) {
                                                            String name = jp.getText();
                                                            jp.nextToken();
                                                            if (jp.nextToken() == JsonToken.START_ARRAY) {
                                                                List<String> values = new ArrayList<String>();
                                                                while (jp.nextToken() != null && jp.getCurrentToken() != JsonToken.END_ARRAY) {
                                                                    if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
                                                                        values.add(jp.getText());
                                                                    }
                                                                }
                                                                parameters.add(new Parameter(name, values));
                                                            }
                                                        }
                                                    }
                                                    break;
                                            }
                                        }
                                        jp.nextToken();
                                        if (jp.getCurrentToken() == JsonToken.END_OBJECT) {
                                            return new ParameterBodyDTO(new ParameterBody(parameters));
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
            }
            return null;
        }
    }
}