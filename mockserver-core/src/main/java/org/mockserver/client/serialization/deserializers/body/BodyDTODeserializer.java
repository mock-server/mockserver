package org.mockserver.client.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.JsonBodyMatchType;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        JsonBodyMatchType matchType = JsonBody.DEFAULT_MATCH_TYPE;
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
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && containsIgnoreCase(jsonParser.getText(), "string", "regex", "json", "jsonSchema", "xpath", "bytes", "parameters", "value")) {
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
                        matchType = JsonBodyMatchType.valueOf(jsonParser.getText());
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Ignoring incorrect JsonBodyMatchType with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.isExpectedStartArrayToken()) {
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
                }
            }
            if (type != null) {
                switch (type) {
                    case STRING:
                        return new StringBodyDTO(new StringBody(valueJsonValue), not);
                    case REGEX:
                        return new RegexBodyDTO(new RegexBody(valueJsonValue), not);
                    case JSON:
                        return new JsonBodyDTO(new JsonBody(valueJsonValue, matchType), not);
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
