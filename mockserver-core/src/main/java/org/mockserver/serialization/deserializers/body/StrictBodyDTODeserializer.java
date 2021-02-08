package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class StrictBodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static final Map<String, Body.Type> fieldNameToType = new HashMap<>();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static ObjectWriter objectWriter;
    private static ObjectMapper objectMapper;
    private static ObjectWriter jsonBodyObjectWriter;

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

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(StrictBodyDTODeserializer.class);

    public StrictBodyDTODeserializer() {
        super(BodyDTO.class);
    }

    @Override
    public BodyDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        BodyDTO result = null;
        JsonToken currentToken = jsonParser.getCurrentToken();
        String valueJsonValue = "";
        byte[] rawBytes = null;
        Body.Type type = null;
        Boolean not = null;
        Boolean optional = null;
        MediaType contentType = null;
        Charset charset = null;
        boolean subString = false;
        MatchType matchType = JsonBody.DEFAULT_MATCH_TYPE;
        Parameters parameters = null;
        Map<String, String> namespacePrefixes = null;
        if (currentToken == JsonToken.START_OBJECT) {
            @SuppressWarnings("unchecked") Map<Object, Object> body = (Map<Object, Object>) ctxt.readValue(jsonParser, Map.class);
            for (Map.Entry<Object, Object> entry : body.entrySet()) {
                if (entry.getKey() instanceof String) {
                    String key = (String) entry.getKey();
                    if (key.equalsIgnoreCase("type")) {
                        try {
                            type = Body.Type.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException iae) {
                            if (MockServerLogger.isEnabled(TRACE)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setMessageFormat("ignoring invalid value for \"type\" field of \"" + entry.getValue() + "\"")
                                        .setThrowable(iae)
                                );
                            }
                        }
                    }
                    if (containsIgnoreCase(key, "string", "regex", "json", "jsonSchema", "jsonPath", "xml", "xmlSchema", "xpath", "base64Bytes") && type != Body.Type.PARAMETERS) {
                        String fieldName = String.valueOf(entry.getKey()).toLowerCase();
                        if (fieldNameToType.containsKey(fieldName)) {
                            type = fieldNameToType.get(fieldName);
                        }
                        if (Map.class.isAssignableFrom(entry.getValue().getClass()) ||
                            containsIgnoreCase(key, "json", "jsonSchema") && !String.class.isAssignableFrom(entry.getValue().getClass())) {
                            if (jsonBodyObjectWriter == null) {
                                jsonBodyObjectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
                            }
                            valueJsonValue = jsonBodyObjectWriter.writeValueAsString(entry.getValue());
                        } else {
                            valueJsonValue = String.valueOf(entry.getValue());
                        }
                    }
                    if (containsIgnoreCase(key, "rawBytes", "base64Bytes")) {
                        if (entry.getValue() instanceof String) {
                            try {
                                rawBytes = BASE64_DECODER.decode((String) entry.getValue());
                            } catch (Throwable throwable) {
                                if (MockServerLogger.isEnabled(DEBUG)) {
                                    MOCK_SERVER_LOGGER.logEvent(
                                        new LogEntry()
                                            .setLogLevel(DEBUG)
                                            .setMessageFormat("invalid base64 encoded rawBytes with value \"" + entry.getValue() + "\"")
                                            .setThrowable(throwable)
                                    );
                                }
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("not")) {
                        not = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    }
                    if (key.equalsIgnoreCase("optional")) {
                        optional = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    }
                    if (key.equalsIgnoreCase("matchType")) {
                        try {
                            matchType = MatchType.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException iae) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring incorrect JsonBodyMatchType with value \"" + entry.getValue() + "\"")
                                        .setThrowable(iae)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("subString")) {
                        try {
                            subString = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException uce) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring unsupported boolean with value \"" + entry.getValue() + "\"")
                                        .setThrowable(uce)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("contentType")) {
                        try {
                            String mediaTypeHeader = String.valueOf(entry.getValue());
                            if (isNotBlank(mediaTypeHeader)) {
                                MediaType parsedMediaTypeHeader = MediaType.parse(mediaTypeHeader);
                                if (isNotBlank(parsedMediaTypeHeader.toString())) {
                                    contentType = parsedMediaTypeHeader;
                                }
                            }
                        } catch (IllegalArgumentException uce) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring unsupported MediaType with value \"" + entry.getValue() + "\"")
                                        .setThrowable(uce)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("charset")) {
                        try {
                            charset = Charset.forName(String.valueOf(entry.getValue()));
                        } catch (UnsupportedCharsetException uce) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring unsupported Charset with value \"" + entry.getValue() + "\"")
                                        .setThrowable(uce)
                                );
                            }
                        } catch (IllegalCharsetNameException icne) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring invalid Charset with value \"" + entry.getValue() + "\"")
                                        .setThrowable(icne)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("parameters")) {
                        if (objectMapper == null) {
                            objectMapper = ObjectMapperFactory.createObjectMapper();
                        }
                        if (objectWriter == null) {
                            objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
                        }
                        parameters = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), Parameters.class);
                    }
                    if (key.equalsIgnoreCase("namespacePrefixes")) {
                        if (objectMapper == null) {
                            objectMapper = ObjectMapperFactory.createObjectMapper();
                        }
                        if (objectWriter == null) {
                            objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
                        }
                        namespacePrefixes = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), new TypeReference<Map<String, String>>(){});
                    }
                  
                }
            }
            if (type != null) {
                switch (type) {
                    case BINARY:
                        if (contentType != null && isNotBlank(contentType.toString())) {
                            result = new BinaryBodyDTO(new BinaryBody(rawBytes, contentType), not);
                            break;
                        } else {
                            result = new BinaryBodyDTO(new BinaryBody(rawBytes), not);
                            break;
                        }
                    case JSON:
                        if (contentType != null && isNotBlank(contentType.toString())) {
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, contentType, matchType), not);
                            break;
                        } else if (charset != null) {
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_JSON_CONTENT_TYPE.withCharset(charset), matchType), not);
                            break;
                        } else {
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_JSON_CONTENT_TYPE, matchType), not);
                            break;
                        }
                    case JSON_SCHEMA:
                        result = new JsonSchemaBodyDTO(new JsonSchemaBody(valueJsonValue), not);
                        break;
                    case JSON_PATH:
                        result = new JsonPathBodyDTO(new JsonPathBody(valueJsonValue), not);
                        break;
                    case PARAMETERS:
                        result = new ParameterBodyDTO(new ParameterBody(parameters), not);
                        break;
                    case REGEX:
                        result = new RegexBodyDTO(new RegexBody(valueJsonValue), not);
                        break;
                    case STRING:
                        if (contentType != null && isNotBlank(contentType.toString())) {
                            result = new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, subString, contentType), not);
                            break;
                        } else if (charset != null) {
                            result = new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, subString, StringBody.DEFAULT_CONTENT_TYPE.withCharset(charset)), not);
                            break;
                        } else {
                            result = new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, subString, null), not);
                            break;
                        }
                    case XML:
                        if (contentType != null && isNotBlank(contentType.toString())) {
                            result = new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, contentType), not);
                            break;
                        } else if (charset != null) {
                            result = new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, XmlBody.DEFAULT_XML_CONTENT_TYPE.withCharset(charset)), not);
                            break;
                        } else {
                            result = new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, XmlBody.DEFAULT_XML_CONTENT_TYPE), not);
                            break;
                        }
                    case XML_SCHEMA:
                        result = new XmlSchemaBodyDTO(new XmlSchemaBody(valueJsonValue), not);
                        break;
                    case XPATH:
                        result = new XPathBodyDTO(new XPathBody(valueJsonValue, namespacePrefixes), not);
                        break;
                }
            }
        }
        if (result != null) {
            result.withOptional(optional);
        }
        return result;
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
