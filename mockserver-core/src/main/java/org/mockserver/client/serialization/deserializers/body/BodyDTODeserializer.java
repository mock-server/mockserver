package org.mockserver.client.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static final Logger logger = LoggerFactory.getLogger(ObjectMapperFactory.class);

    private static Map<String, Body.Type> fieldNameToType = new HashMap<String, Body.Type>();

    static {
        fieldNameToType.put("string".toLowerCase(), Body.Type.STRING);
        fieldNameToType.put("regex".toLowerCase(), Body.Type.REGEX);
        fieldNameToType.put("json".toLowerCase(), Body.Type.JSON);
        fieldNameToType.put("jsonSchema".toLowerCase(), Body.Type.JSON_SCHEMA);
        fieldNameToType.put("xpath".toLowerCase(), Body.Type.XPATH);
        fieldNameToType.put("bytes".toLowerCase(), Body.Type.BINARY);
        fieldNameToType.put("parameters".toLowerCase(), Body.Type.PARAMETERS);
    }

    public BodyDTODeserializer() {
        super(BodyDTO.class);
    }

    @Override
    public BodyDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        JsonToken currentToken = jsonParser.getCurrentToken();
        String valueJsonValue = "";
        Body.Type type = null;
        boolean not = false;
        Charset charset = null;
        MatchType matchType = JsonBody.DEFAULT_MATCH_TYPE;
        List<Parameter> parameters = new ArrayList<Parameter>();
        if (currentToken == JsonToken.START_OBJECT) {
            while (jsonParser.getCurrentToken() != JsonToken.END_OBJECT) {
                jsonParser.nextToken();
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("type")) {
                    jsonParser.nextToken();
                    try {
                        type = Body.Type.valueOf(jsonParser.getText());
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Ignoring invalid value for \"type\" field of \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && containsIgnoreCase(jsonParser.getText(), "string", "regex", "json", "jsonSchema", "xpath", "bytes", "value") && type != Body.Type.PARAMETERS) {
                    String fieldName = jsonParser.getText().toLowerCase();
                    if (fieldNameToType.containsKey(fieldName)) {
                        type = fieldNameToType.get(fieldName);
                    }
                    jsonParser.nextToken();
                    if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                        valueJsonValue = jsonParser.getText();
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("not")) {
                    jsonParser.nextToken();
                    not = Boolean.parseBoolean(jsonParser.getText());
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("matchType")) {
                    jsonParser.nextToken();
                    try {
                        matchType = MatchType.valueOf(jsonParser.getText());
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Ignoring incorrect JsonBodyMatchType with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("charset")) {
                    jsonParser.nextToken();
                    try {
                        charset = Charset.forName(jsonParser.getText());
                    } catch (UnsupportedCharsetException uce) {
                        logger.warn("Ignoring unsupported Charset with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && containsIgnoreCase(jsonParser.getText(), "parameters", "value")) {
                    jsonParser.nextToken();
                    if (jsonParser.isExpectedStartArrayToken()) {
                        int objectDepth = 1;
                        String parameterName = "";
                        List<String> parameterValues = new ArrayList<String>();
                        boolean parameterNot = false;
                        while (objectDepth > 0) {
                            JsonToken token = jsonParser.getCurrentToken();
                            switch (token) {
                                case START_ARRAY:
                                    break;
                                case START_OBJECT:
                                    objectDepth++;
                                    parameterName = "";
                                    parameterValues = new ArrayList<String>();
                                    parameterNot = false;
                                    break;
                                case END_OBJECT:
                                    objectDepth--;
                                    if (objectDepth >= 1) {
                                        if (parameterNot) {
                                            parameters.add(Not.not(new Parameter(parameterName, parameterValues)));
                                        } else {
                                            parameters.add(new Parameter(parameterName, parameterValues));
                                        }
                                    }
                                    break;
                                case FIELD_NAME:
                                    if (jsonParser.getText().equalsIgnoreCase("name")) {
                                        jsonParser.nextToken();
                                        parameterName = jsonParser.getText();
                                    } else if (jsonParser.getText().equalsIgnoreCase("not")) {
                                        jsonParser.nextToken();
                                        parameterNot = Boolean.parseBoolean(jsonParser.getText());
                                    } else if (jsonParser.getText().equalsIgnoreCase("values")) {
                                        if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
                                            while (jsonParser.nextToken() != null && jsonParser.getCurrentToken() != JsonToken.END_ARRAY) {
                                                if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {
                                                    parameterValues.add(jsonParser.getText());
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case VALUE_TRUE:
                                    break;
                                case VALUE_STRING:
                                    break;
                            }
                            if (objectDepth > 0) {
                                jsonParser.nextToken();
                            }
                        }
                    }
                }
            }
            if (type != null) {
                switch (type) {
                    case STRING:
                        return new StringBodyDTO(new StringBody(valueJsonValue, charset), not);
                    case REGEX:
                        return new RegexBodyDTO(new RegexBody(valueJsonValue), not);
                    case JSON:
                        return new JsonBodyDTO(new JsonBody(valueJsonValue, charset, matchType), not);
                    case JSON_SCHEMA:
                        return new JsonSchemaBodyDTO(new JsonSchemaBody(valueJsonValue), not);
                    case XPATH:
                        return new XPathBodyDTO(new XPathBody(valueJsonValue), not);
                    case BINARY:
                        return new BinaryBodyDTO(new BinaryBody(Base64Converter.base64StringToBytes(valueJsonValue)), not);
                    case PARAMETERS:
                        return new ParameterBodyDTO(new ParameterBody(parameters), not);
                }
            }
        } else if (currentToken == JsonToken.VALUE_STRING) {
            return new StringBodyDTO(new StringBody(jsonParser.getText()));
        }
        return null;
    }

    private boolean containsIgnoreCase(String valueToMatch, String... listOfValues) {
        for (String item : listOfValues) {
            if (item.equalsIgnoreCase(valueToMatch)) {
                return true;
            }
        }
        return false;
    }
}
