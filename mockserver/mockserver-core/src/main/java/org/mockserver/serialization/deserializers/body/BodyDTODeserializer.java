package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.serialization.ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class BodyDTODeserializer extends StdDeserializer<BodyDTO> {

    private static final long serialVersionUID = 1L;

    private static final Map<String, Body.Type> fieldNameToType = new HashMap<>();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static final ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
    private static final ObjectWriter jsonBodyObjectWriter = buildObjectMapperWithoutRemovingEmptyValues().writerWithDefaultPrettyPrinter();

    static {
        fieldNameToType.put("base64Bytes".toLowerCase(), Body.Type.BINARY);
        fieldNameToType.put("json".toLowerCase(), Body.Type.JSON);
        fieldNameToType.put("jsonSchema".toLowerCase(), Body.Type.JSON_SCHEMA);
        fieldNameToType.put("jsonPath".toLowerCase(), Body.Type.JSON_PATH);
        fieldNameToType.put("parameters".toLowerCase(), Body.Type.PARAMETERS);
        fieldNameToType.put("regex".toLowerCase(), Body.Type.REGEX);
        fieldNameToType.put("fuzzy".toLowerCase(), Body.Type.FUZZY);
        fieldNameToType.put("string".toLowerCase(), Body.Type.STRING);
        fieldNameToType.put("xml".toLowerCase(), Body.Type.XML);
        fieldNameToType.put("xmlSchema".toLowerCase(), Body.Type.XML_SCHEMA);
        fieldNameToType.put("xpath".toLowerCase(), Body.Type.XPATH);
        fieldNameToType.put("jsonRpc".toLowerCase(), Body.Type.JSON_RPC);
        fieldNameToType.put("graphql".toLowerCase(), Body.Type.GRAPHQL);
        fieldNameToType.put("filePath".toLowerCase(), Body.Type.FILE);
        fieldNameToType.put("moduleName".toLowerCase(), Body.Type.WASM);
        fieldNameToType.put("filenames".toLowerCase(), Body.Type.MULTIPART);
        fieldNameToType.put("partContentTypes".toLowerCase(), Body.Type.MULTIPART);
    }

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(BodyDTODeserializer.class);

    public BodyDTODeserializer() {
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
        double fuzzyThreshold = FuzzyBody.DEFAULT_THRESHOLD;
        boolean fuzzyIgnoreCase = FuzzyBody.DEFAULT_IGNORE_CASE;
        MatchType matchType = JsonBody.DEFAULT_MATCH_TYPE;
        boolean matchNumbersAsStrings = false;
        Parameters parameters = null;
        Parameters multipartFields = null;
        Parameters multipartFilenames = null;
        Parameters multipartPartContentTypes = null;
        Map<String, ParameterStyle> parameterStyles = null;
        Map<String, String> namespacePrefixes = null;
        Object dateTimeValue = null;
        boolean explicitType = false;
        String jsonRpcMethod = null;
        String jsonRpcParamsSchema = null;
        String methodFieldValue = null;
        String paramsSchemaFieldValue = null;
        String graphQLQuery = null;
        String graphQLOperationName = null;
        String graphQLVariablesSchema = null;
        SelectionSetMatchType graphQLSelectionSetMatchType = null;
        @SuppressWarnings("unchecked")
        List<String> graphQLFields = null;
        String graphQLSchema = null;
        String queryFieldValue = null;
        String operationNameFieldValue = null;
        String variablesSchemaFieldValue = null;
        SelectionSetMatchType selectionSetMatchTypeFieldValue = null;
        @SuppressWarnings("unchecked")
        List<String> fieldsFieldValue = null;
        String schemaFieldValue = null;
        List<BodyDTO> allOfBodies = null;
        if (currentToken == JsonToken.START_OBJECT) {
            @SuppressWarnings("unchecked") Map<Object, Object> body = (Map<Object, Object>) ctxt.readValue(jsonParser, Map.class);
            for (Map.Entry<Object, Object> entry : body.entrySet()) {
                if (entry.getKey() instanceof String) {
                    String key = (String) entry.getKey();
                    if (key.equalsIgnoreCase("type")) {
                        try {
                            type = Body.Type.valueOf(String.valueOf(entry.getValue()));
                            explicitType = true;
                        } catch (IllegalArgumentException iae) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring invalid value for \"type\" field of \"" + entry.getValue() + "\"")
                                        .setThrowable(iae)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("jsonRpc") && entry.getValue() instanceof Map) {
                        type = Body.Type.JSON_RPC;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> jsonRpcMap = (Map<String, Object>) entry.getValue();
                        if (jsonRpcMap.containsKey("method")) {
                            jsonRpcMethod = String.valueOf(jsonRpcMap.get("method"));
                        }
                        if (jsonRpcMap.containsKey("paramsSchema")) {
                            jsonRpcParamsSchema = jsonBodyObjectWriter.writeValueAsString(jsonRpcMap.get("paramsSchema"));
                        }
                    }
                    if (key.equalsIgnoreCase("graphQL") && entry.getValue() instanceof Map) {
                        type = Body.Type.GRAPHQL;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphQLMap = (Map<String, Object>) entry.getValue();
                        if (graphQLMap.containsKey("query")) {
                            graphQLQuery = String.valueOf(graphQLMap.get("query"));
                        }
                        if (graphQLMap.containsKey("operationName")) {
                            graphQLOperationName = String.valueOf(graphQLMap.get("operationName"));
                        }
                        if (graphQLMap.containsKey("variablesSchema")) {
                            graphQLVariablesSchema = jsonBodyObjectWriter.writeValueAsString(graphQLMap.get("variablesSchema"));
                        }
                        if (graphQLMap.containsKey("selectionSetMatchType")) {
                            try {
                                graphQLSelectionSetMatchType = SelectionSetMatchType.valueOf(String.valueOf(graphQLMap.get("selectionSetMatchType")));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        if (graphQLMap.containsKey("fields") && graphQLMap.get("fields") instanceof List) {
                            graphQLFields = ((List<?>) graphQLMap.get("fields")).stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.toList());
                        }
                        if (graphQLMap.containsKey("schema")) {
                            Object schemaValue = graphQLMap.get("schema");
                            if (schemaValue instanceof String) {
                                graphQLSchema = String.valueOf(schemaValue);
                            } else if (schemaValue != null) {
                                graphQLSchema = jsonBodyObjectWriter.writeValueAsString(schemaValue);
                            }
                        }
                    }
                    if (containsIgnoreCase(key, "string", "regex", "fuzzy", "json", "jsonSchema", "jsonPath", "xml", "xmlSchema", "xpath", "base64Bytes", "filePath", "moduleName") && type != Body.Type.PARAMETERS) {
                        String fieldName = String.valueOf(entry.getKey()).toLowerCase();
                        if (fieldNameToType.containsKey(fieldName)) {
                            type = fieldNameToType.get(fieldName);
                        }
                        if (Map.class.isAssignableFrom(entry.getValue().getClass()) ||
                            containsIgnoreCase(key, "json", "jsonSchema") && !String.class.isAssignableFrom(entry.getValue().getClass())) {
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
                    if (key.equalsIgnoreCase("matchNumbersAsStrings")) {
                        matchNumbersAsStrings = Boolean.parseBoolean(String.valueOf(entry.getValue()));
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
                    if (key.equalsIgnoreCase("threshold")) {
                        try {
                            fuzzyThreshold = Double.parseDouble(String.valueOf(entry.getValue()));
                        } catch (NumberFormatException nfe) {
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("ignoring invalid fuzzy threshold with value \"" + entry.getValue() + "\"")
                                        .setThrowable(nfe)
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("ignoreCase")) {
                        fuzzyIgnoreCase = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    }
                    if (key.equalsIgnoreCase("parameterStyles") && entry.getValue() instanceof Map) {
                        try {
                            parameterStyles = new HashMap<>();
                            for (Map.Entry<?, ?> parameterStyle : ((Map<?, ?>) entry.getValue()).entrySet()) {
                                parameterStyles.put(String.valueOf(parameterStyle.getKey()), ParameterStyle.valueOf(String.valueOf(parameterStyle.getValue())));
                            }
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
                    if (key.equalsIgnoreCase(BodyDateTimeField.FIELD_NAME)) {
                        dateTimeValue = entry.getValue();
                    }
                    if (key.equalsIgnoreCase("namespacePrefixes") && entry.getValue() instanceof Map) {
                      try {
                          namespacePrefixes = new HashMap<>();
                          for (Map.Entry<?, ?> namespacePrefixEntry : ((Map<?, ?>) entry.getValue()).entrySet()) {
                              namespacePrefixes.put(String.valueOf(namespacePrefixEntry.getKey()), String.valueOf(namespacePrefixEntry.getValue()));
                          }
                      } catch (IllegalArgumentException uce) {
                          if (MockServerLogger.isEnabled(DEBUG)) {
                              MOCK_SERVER_LOGGER.logEvent(
                                  new LogEntry()
                                      .setLogLevel(DEBUG)
                                      .setMessageFormat("ignoring unsupported namespacePrefixEntry with value \"" + entry.getValue() + "\"")
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
                    if (key.equalsIgnoreCase("method") && entry.getValue() instanceof String) {
                        methodFieldValue = String.valueOf(entry.getValue());
                    }
                    if (key.equalsIgnoreCase("paramsSchema")) {
                        if (entry.getValue() instanceof String) {
                            paramsSchemaFieldValue = String.valueOf(entry.getValue());
                        } else if (entry.getValue() instanceof Map) {
                            paramsSchemaFieldValue = jsonBodyObjectWriter.writeValueAsString(entry.getValue());
                        }
                    }
                    if (key.equalsIgnoreCase("query") && entry.getValue() instanceof String) {
                        queryFieldValue = String.valueOf(entry.getValue());
                    }
                    if (key.equalsIgnoreCase("operationName") && entry.getValue() instanceof String) {
                        operationNameFieldValue = String.valueOf(entry.getValue());
                    }
                    if (key.equalsIgnoreCase("variablesSchema")) {
                        if (entry.getValue() instanceof String) {
                            variablesSchemaFieldValue = String.valueOf(entry.getValue());
                        } else if (entry.getValue() instanceof Map) {
                            variablesSchemaFieldValue = jsonBodyObjectWriter.writeValueAsString(entry.getValue());
                        }
                    }
                    if (key.equalsIgnoreCase("selectionSetMatchType") && entry.getValue() instanceof String) {
                        try {
                            selectionSetMatchTypeFieldValue = SelectionSetMatchType.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (key.equalsIgnoreCase("fields") && entry.getValue() instanceof List) {
                        fieldsFieldValue = ((List<?>) entry.getValue()).stream()
                            .map(String::valueOf)
                            .collect(java.util.stream.Collectors.toList());
                    }
                    if (key.equalsIgnoreCase("schema")) {
                        if (entry.getValue() instanceof String) {
                            schemaFieldValue = String.valueOf(entry.getValue());
                        } else if (entry.getValue() != null) {
                            schemaFieldValue = jsonBodyObjectWriter.writeValueAsString(entry.getValue());
                        }
                    }
                    if (key.equalsIgnoreCase("parameters")) {
                        parameters = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), Parameters.class);
                    }
                    if (key.equalsIgnoreCase("fields") && !(entry.getValue() instanceof List)) {
                        type = Body.Type.MULTIPART;
                        multipartFields = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), Parameters.class);
                    }
                    if (key.equalsIgnoreCase("filenames")) {
                        type = Body.Type.MULTIPART;
                        multipartFilenames = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), Parameters.class);
                    }
                    if (key.equalsIgnoreCase("partContentTypes")) {
                        type = Body.Type.MULTIPART;
                        multipartPartContentTypes = objectMapper.readValue(objectWriter.writeValueAsString(entry.getValue()), Parameters.class);
                    }
                    if (key.equalsIgnoreCase("bodyAllOf") && (entry.getValue() instanceof List || entry.getValue() instanceof Object[])) {
                        type = Body.Type.ALL_OF;
                        allOfBodies = new java.util.ArrayList<>();
                        // the configured ObjectMapper deserialises JSON arrays as Object[] (not List)
                        List<?> elements = entry.getValue() instanceof Object[]
                            ? java.util.Arrays.asList((Object[]) entry.getValue())
                            : (List<?>) entry.getValue();
                        for (Object element : elements) {
                            BodyDTO childBody = objectMapper.readValue(objectWriter.writeValueAsString(element), BodyDTO.class);
                            if (childBody != null) {
                                allOfBodies.add(childBody);
                            }
                        }
                    }
                }
            }
            NottableDateTimeString dateTime = BodyDateTimeField.forBody(objectMapper, dateTimeValue, type, explicitType);
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
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, contentType, matchType, matchNumbersAsStrings), not);
                            break;
                        } else if (charset != null) {
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_JSON_CONTENT_TYPE.withCharset(charset), matchType, matchNumbersAsStrings), not);
                            break;
                        } else {
                            result = new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_JSON_CONTENT_TYPE, matchType, matchNumbersAsStrings), not);
                            break;
                        }
                    case JSON_SCHEMA:
                        result = new JsonSchemaBodyDTO(new JsonSchemaBody(valueJsonValue).withParameterStyles(parameterStyles), not);
                        break;
                    case JSON_PATH:
                        result = new JsonPathBodyDTO(new JsonPathBody(valueJsonValue, dateTime), not);
                        break;
                    case PARAMETERS:
                        result = new ParameterBodyDTO(new ParameterBody(parameters), not);
                        break;
                    case MULTIPART:
                        result = new MultipartBodyDTO(new MultipartBody(multipartFields, multipartFilenames, multipartPartContentTypes), not);
                        break;
                    case REGEX:
                        result = new RegexBodyDTO(new RegexBody(valueJsonValue), not);
                        break;
                    case FUZZY:
                        result = new FuzzyBodyDTO(new FuzzyBody(valueJsonValue, fuzzyThreshold, fuzzyIgnoreCase), not);
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
                        result = new XPathBodyDTO(new XPathBody(valueJsonValue, namespacePrefixes, dateTime), not);
                        break;
                    case JSON_RPC:
                        result = new JsonRpcBodyDTO(new JsonRpcBody(
                            jsonRpcMethod != null ? jsonRpcMethod : methodFieldValue,
                            jsonRpcParamsSchema != null ? jsonRpcParamsSchema : paramsSchemaFieldValue
                        ), not);
                        break;
                    case GRAPHQL:
                        GraphQLBody graphQLBody = new GraphQLBody(
                            graphQLQuery != null ? graphQLQuery : queryFieldValue,
                            graphQLOperationName != null ? graphQLOperationName : operationNameFieldValue,
                            graphQLVariablesSchema != null ? graphQLVariablesSchema : variablesSchemaFieldValue
                        );
                        SelectionSetMatchType resolvedMatchType = graphQLSelectionSetMatchType != null ? graphQLSelectionSetMatchType : selectionSetMatchTypeFieldValue;
                        if (resolvedMatchType != null) {
                            graphQLBody.withSelectionSetMatchType(resolvedMatchType);
                        }
                        List<String> resolvedFields = graphQLFields != null ? graphQLFields : fieldsFieldValue;
                        if (resolvedFields != null) {
                            graphQLBody.withFields(resolvedFields);
                        }
                        String resolvedSchema = graphQLSchema != null ? graphQLSchema : schemaFieldValue;
                        if (resolvedSchema != null) {
                            graphQLBody.withSchema(resolvedSchema);
                        }
                        result = new GraphQLBodyDTO(graphQLBody, not);
                        break;
                    case FILE:
                        result = new FileBodyDTO(new FileBody(valueJsonValue, contentType), not);
                        break;
                    case WASM:
                        result = new WasmBodyDTO(new WasmBody(valueJsonValue), not);
                        break;
                    case ALL_OF:
                        result = new AllOfBodyDTO(allOfBodies, not);
                        break;
                }
            } else if (body.size() > 0) {
                result = new JsonBodyDTO(new JsonBody(jsonBodyObjectWriter.writeValueAsString(body), JsonBody.DEFAULT_MATCH_TYPE), null);
            }
        } else if (currentToken == JsonToken.START_ARRAY) {
            result = new JsonBodyDTO(new JsonBody(jsonBodyObjectWriter.writeValueAsString(ctxt.readValue(jsonParser, List.class)), JsonBody.DEFAULT_MATCH_TYPE), null);
        } else if (currentToken == JsonToken.VALUE_STRING) {
            result = new StringBodyDTO(new StringBody(jsonParser.getText()));
        }
        if (result == null && jsonParser.currentToken() == JsonToken.END_OBJECT) {
            result = new JsonBodyDTO(JsonBody.json("{ }"));
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
