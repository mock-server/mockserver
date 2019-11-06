package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.net.MediaType;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.mockserver.serialization.Base64Converter;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static Map<String, Body.Type> fieldNameToType = new HashMap<>();
    private static ObjectMapper objectMapper;

    static {
        fieldNameToType.put("base64Bytes".toLowerCase(), Body.Type.BINARY);
        fieldNameToType.put("json".toLowerCase(), Body.Type.JSON);
        fieldNameToType.put("jsonSchema".toLowerCase(), Body.Type.JSON_SCHEMA);
        fieldNameToType.put("jsonPath".toLowerCase(), Body.Type.JSON_PATH);
        fieldNameToType.put("parameters".toLowerCase(), Body.Type.PARAMETERS);
        fieldNameToType.put("regex".toLowerCase(), Body.Type.REGEX);
        fieldNameToType.put("string".toLowerCase(), Body.Type.STRING);
        fieldNameToType.put("xml".toLowerCase(), Body.Type.XML);
        fieldNameToType.put("xmlSchema".toLowerCase(), Body.Type.XML_SCHEMA);
        fieldNameToType.put("xpath".toLowerCase(), Body.Type.XPATH);
    }

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(BodyDTODeserializer.class);
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
            @SuppressWarnings("unchecked") Map<Object, Object> body = (Map<Object, Object>) ctxt.readValue(jsonParser, Map.class);
            for (Map.Entry<Object, Object> entry : body.entrySet()) {
                if (entry.getKey() instanceof String) {
                    String key = (String) entry.getKey();
                    if (key.equalsIgnoreCase("type")) {
                        try {
                            type = Body.Type.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException iae) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring invalid value for \"type\" field of \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (containsIgnoreCase(key, "string", "regex", "json", "jsonSchema", "jsonPath", "xml", "xmlSchema", "xpath", "base64Bytes") && type != Body.Type.PARAMETERS) {
                        String fieldName = String.valueOf(entry.getKey()).toLowerCase();
                        if (fieldNameToType.containsKey(fieldName)) {
                            type = fieldNameToType.get(fieldName);
                        }
                        valueJsonValue = String.valueOf(entry.getValue());
                    }
                    if (key.equalsIgnoreCase("not")) {
                        not = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    }
                    if (key.equalsIgnoreCase("matchType")) {
                        try {
                            matchType = MatchType.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException iae) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring incorrect JsonBodyMatchType with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("subString")) {
                        try {
                            subString = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring unsupported boolean with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("contentType")) {
                        try {
                            contentType = MediaType.parse(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring unsupported MediaType with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("charset")) {
                        try {
                            charset = Charset.forName(String.valueOf(entry.getValue()));
                        } catch (UnsupportedCharsetException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring unsupported Charset with value \"" + entry.getValue() + "\"")
                            );
                        } catch (IllegalCharsetNameException icne) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("Ignoring invalid Charset with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("parameters")) {
                        if (objectMapper == null) {
                            objectMapper = ObjectMapperFactory.createObjectMapper();
                        }
                        parameters = objectMapper.readValue(objectMapper.writeValueAsString(entry.getValue()), Parameters.class);
                    }
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
                    case JSON_PATH:
                        return new JsonPathBodyDTO(new JsonPathBody(valueJsonValue), not);
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
            } else if (body.size() > 0) {
                if (objectMapper == null) {
                    objectMapper = ObjectMapperFactory.createObjectMapper();
                }
                return new JsonBodyDTO(new JsonBody(objectMapper.writeValueAsString(body), JsonBody.DEFAULT_MATCH_TYPE), false);
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
