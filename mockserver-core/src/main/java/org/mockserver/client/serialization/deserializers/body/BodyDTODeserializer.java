package org.mockserver.client.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.net.MediaType;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.model.*;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static Map<String, Body.Type> fieldNameToType = new HashMap<>();

    static {
        fieldNameToType.put("base64Bytes".toLowerCase(), Body.Type.BINARY);
        fieldNameToType.put("json".toLowerCase(), Body.Type.JSON);
        fieldNameToType.put("jsonSchema".toLowerCase(), Body.Type.JSON_SCHEMA);
        fieldNameToType.put("parameters".toLowerCase(), Body.Type.PARAMETERS);
        fieldNameToType.put("regex".toLowerCase(), Body.Type.REGEX);
        fieldNameToType.put("string".toLowerCase(), Body.Type.STRING);
        fieldNameToType.put("xml".toLowerCase(), Body.Type.XML);
        fieldNameToType.put("xmlSchema".toLowerCase(), Body.Type.XML_SCHEMA);
        fieldNameToType.put("xpath".toLowerCase(), Body.Type.XPATH);
    }

    private final MockServerLogger mockServerLogger = new MockServerLogger(BodyDTODeserializer.class);
    private final Base64Converter base64Converter = new Base64Converter();

    public BodyDTODeserializer() {
        super(BodyDTO.class);
    }

    @Override
    public BodyDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        JsonToken currentToken = jsonParser.getCurrentToken();
        String valueJsonValue = "";
        Body.Type type = null;
        boolean not = false;
        boolean subString = false;
        MediaType contentType = null;
        Charset charset = null;
        MatchType matchType = JsonBody.DEFAULT_MATCH_TYPE;
        Parameters parameters = null;
        if (currentToken == JsonToken.START_OBJECT) {
            while (jsonParser.getCurrentToken() != JsonToken.END_OBJECT) {
                jsonParser.nextToken();
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("type")) {
                    jsonParser.nextToken();
                    try {
                        type = Body.Type.valueOf(jsonParser.getText());
                    } catch (IllegalArgumentException iae) {
                        mockServerLogger.trace("Ignoring invalid value for \"type\" field of \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && containsIgnoreCase(jsonParser.getText(), "string", "regex", "json", "jsonSchema", "xpath", "xml", "xmlSchema", "base64Bytes") && type != Body.Type.PARAMETERS) {
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
                        mockServerLogger.trace("Ignoring incorrect JsonBodyMatchType with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("subString")) {
                    jsonParser.nextToken();
                    try {
                        subString = jsonParser.getBooleanValue();
                    } catch (IllegalArgumentException uce) {
                        mockServerLogger.trace("Ignoring unsupported boolean with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("contentType")) {
                    jsonParser.nextToken();
                    try {
                        contentType = MediaType.parse(jsonParser.getText());
                    } catch (IllegalArgumentException uce) {
                        mockServerLogger.trace("Ignoring unsupported MediaType with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("charset")) {
                    jsonParser.nextToken();
                    try {
                        charset = Charset.forName(jsonParser.getText());
                    } catch (UnsupportedCharsetException uce) {
                        mockServerLogger.trace("Ignoring unsupported Charset with value \"" + jsonParser.getText() + "\"");
                    } catch (IllegalCharsetNameException icne) {
                        mockServerLogger.trace("Ignoring invalid Charset with value \"" + jsonParser.getText() + "\"");
                    }
                }
                if (jsonParser.getCurrentToken() == JsonToken.FIELD_NAME && jsonParser.getText().equalsIgnoreCase("parameters")) {
                    jsonParser.nextToken();
                    parameters = jsonParser.readValueAs(Parameters.class);
                }
            }
            if (type != null) {
                switch (type) {
                    case BINARY:
                        if (contentType != null) {
                            return new BinaryBodyDTO(new BinaryBody(base64Converter.base64StringToBytes(valueJsonValue), contentType), not);
                        } else {
                            return new BinaryBodyDTO(new BinaryBody(base64Converter.base64StringToBytes(valueJsonValue)), not);
                        }
                    case JSON:
                        if (contentType != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, contentType, matchType), not);
                        } else if (charset != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, charset, matchType), not);
                        } else {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, matchType), not);
                        }
                    case JSON_SCHEMA:
                        return new JsonSchemaBodyDTO(new JsonSchemaBody(valueJsonValue), not);
                    case PARAMETERS:
                        return new ParameterBodyDTO(new ParameterBody(parameters), not);
                    case REGEX:
                        return new RegexBodyDTO(new RegexBody(valueJsonValue), not);
                    case STRING:
                        if (contentType != null) {
                            return new StringBodyDTO(new StringBody(valueJsonValue, subString, contentType), not);
                        } else if (charset != null) {
                            return new StringBodyDTO(new StringBody(valueJsonValue, subString, charset), not);
                        } else {
                            return new StringBodyDTO(new StringBody(valueJsonValue, subString), not);
                        }
                    case XML:
                        if (contentType != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, contentType), not);
                        } else if (charset != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, charset), not);
                        } else {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue), not);
                        }
                    case XML_SCHEMA:
                        return new XmlSchemaBodyDTO(new XmlSchemaBody(valueJsonValue), not);
                    case XPATH:
                        return new XPathBodyDTO(new XPathBody(valueJsonValue), not);
                }
            }
        } else if (currentToken == JsonToken.VALUE_STRING) {
            return new StringBodyDTO(new StringBody(jsonParser.getText()));
        }
        return null;
    }

    private NottableString parseNottableString(JsonParser jsonParser) throws IOException {
        NottableString nottableString = null;

        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {

            boolean isNot = false;
            String value = "";

            JsonToken currentToken;
            while ((currentToken = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
                switch (currentToken) {
                    case START_ARRAY:
                        break;
                    case START_OBJECT:
                        break;
                    case END_OBJECT:
                        break;
                    case FIELD_NAME:
                        if (jsonParser.getText().equalsIgnoreCase("not")) {
                            isNot = jsonParser.nextToken() == JsonToken.VALUE_TRUE;
                        } else if (jsonParser.getText().equalsIgnoreCase("value")) {
                            jsonParser.nextToken();
                            value = jsonParser.getText();
                        }
                        break;
                    case VALUE_TRUE:
                        break;
                    case VALUE_STRING:
                        break;
                }
            }

            nottableString = string(value, isNot);

        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING) {

            String text = jsonParser.getText();
            if (text.startsWith("!")) {
                nottableString = NottableString.not(text.replaceFirst("^!", ""));
            } else {
                nottableString = string(text);
            }

        }

        return nottableString;
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
