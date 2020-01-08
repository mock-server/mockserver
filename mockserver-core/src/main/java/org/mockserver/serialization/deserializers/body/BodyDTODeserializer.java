package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static final Map<String, Body.Type> fieldNameToType = new HashMap<>();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
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

    public BodyDTODeserializer() {
        super(BodyDTO.class);
    }

    @Override
    public BodyDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        JsonToken currentToken = jsonParser.getCurrentToken();
        String valueJsonValue = "";
        byte[] rawBinaryData = null;
        Body.Type type = null;
        boolean not = false;
        MediaType contentType = null;
        Charset charset = null;
        boolean subString = false;
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
                                    .setMessageFormat("ignoring invalid value for \"type\" field of \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (containsIgnoreCase(key, "string", "regex", "json", "jsonSchema", "jsonPath", "xml", "xmlSchema", "xpath", "base64Bytes") && type != Body.Type.PARAMETERS) {
                        String fieldName = String.valueOf(entry.getKey()).toLowerCase();
                        if (fieldNameToType.containsKey(fieldName)) {
                            type = fieldNameToType.get(fieldName);
                        }
                        if (Map.class.isAssignableFrom(entry.getValue().getClass())) {
                            if (objectMapper == null) {
                                objectMapper = ObjectMapperFactory.createObjectMapper();
                            }
                            valueJsonValue = objectMapper.writeValueAsString(entry.getValue());
                        } else {
                            valueJsonValue = String.valueOf(entry.getValue());
                        }
                    }
                    if (containsIgnoreCase(key, "rawBinaryData", "base64Bytes")) {
                        if (entry.getValue() instanceof String) {
                            try {
                                rawBinaryData = BASE64_DECODER.decode((String) entry.getValue());
                            } catch (Throwable throwable) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setType(LogEntry.LogMessageType.TRACE)
                                        .setLogLevel(TRACE)
                                        .setMessageFormat("invalid base64 encoded rawBinaryData with value \"" + entry.getValue() + "\"")
                                );
                            }
                        }
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
                                    .setMessageFormat("ignoring incorrect JsonBodyMatchType with value \"" + entry.getValue() + "\"")
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
                                    .setMessageFormat("ignoring unsupported boolean with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("contentType")) {
                        try {
                            String mediaTypeHeader = String.valueOf(entry.getValue());
                            if (isNotBlank(mediaTypeHeader)) {
                                contentType = MediaType.parse(mediaTypeHeader);
                            }
                        } catch (IllegalArgumentException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring unsupported MediaType with value \"" + entry.getValue() + "\"")
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
                                    .setMessageFormat("ignoring unsupported Charset with value \"" + entry.getValue() + "\"")
                            );
                        } catch (IllegalCharsetNameException icne) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setType(LogEntry.LogMessageType.TRACE)
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring invalid Charset with value \"" + entry.getValue() + "\"")
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
                            return new BinaryBodyDTO(new BinaryBody(rawBinaryData, contentType), not);
                        } else {
                            return new BinaryBodyDTO(new BinaryBody(rawBinaryData), not);
                        }
                    case JSON:
                        if (contentType != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBinaryData, contentType, matchType), not);
                        } else if (charset != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBinaryData, JsonBody.DEFAULT_CONTENT_TYPE.withCharset(charset), matchType), not);
                        } else {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBinaryData, JsonBody.DEFAULT_CONTENT_TYPE, matchType), not);
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
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBinaryData, subString, contentType), not);
                        } else if (charset != null) {
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBinaryData, subString, StringBody.DEFAULT_CONTENT_TYPE.withCharset(charset)), not);
                        } else {
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBinaryData, subString, null), not);
                        }
                    case XML:
                        if (contentType != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBinaryData, contentType), not);
                        } else if (charset != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBinaryData, XmlBody.DEFAULT_CONTENT_TYPE.withCharset(charset)), not);
                        } else {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBinaryData, XmlBody.DEFAULT_CONTENT_TYPE), not);
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

    private boolean containsIgnoreCase(String valueToMatch, String... listOfValues) {
        for (String item : listOfValues) {
            if (item.equalsIgnoreCase(valueToMatch)) {
                return true;
            }
        }
        return false;
    }
}
