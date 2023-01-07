package org.mockserver.validator.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.validator.Validator;
import org.mockserver.version.Version;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    public static final String OPEN_API_SPECIFICATION_URL = "OpenAPI Specification: https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/" + Version.getMajorMinorVersion() + ".x" + NEW_LINE +
        "Documentation: https://mock-server.com/mock_server/creating_expectations.html";
    private static final Map<String, String> schemaCache = new ConcurrentHashMap<>();
    // using draft 07 as default due to TLS issues downloading draft 2019-09 which causes errors
    private static final SpecVersion.VersionFlag DEFAULT_JSON_SCHEMA_VERSION = SpecVersion.VersionFlag.V7;
    private final MockServerLogger mockServerLogger;
    private final Class<?> type;
    private final String schema;
    private final JsonNode schemaJsonNode;
    private JsonSchema validator;
    private final static ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        this.type = null;
        if (schema.trim().endsWith(".json")) {
            this.schema = FileReader.readFileFromClassPathOrPath(schema);
        } else if (schema.trim().endsWith("}")) {
            this.schema = schema;
        } else {
            throw new IllegalArgumentException("Schema must either be a path reference to a *.json file or a json string");
        }
        this.schemaJsonNode = getSchemaJsonNode();
        this.validator = getJsonSchemaFactory(this.schemaJsonNode).getSchema(this.schemaJsonNode);
    }

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String schema, JsonNode schemaJsonNode) {
        this.mockServerLogger = mockServerLogger;
        this.type = null;
        this.schema = schema;
        this.schemaJsonNode = schemaJsonNode;
        this.validator = getJsonSchemaFactory(this.schemaJsonNode).getSchema(this.schemaJsonNode);
    }

    public JsonSchemaValidator(MockServerLogger mockServerLogger, Class<?> type, String routePath, String mainSchemeFile, String... referenceFiles) {
        this.mockServerLogger = mockServerLogger;
        this.type = type;
        if (!schemaCache.containsKey(mainSchemeFile)) {
            schemaCache.put(mainSchemeFile, addReferencesIntoSchema(routePath, mainSchemeFile, referenceFiles));
        }
        this.schema = schemaCache.get(mainSchemeFile);
        this.schemaJsonNode = getSchemaJsonNode();
        this.validator = getJsonSchemaFactory(this.schemaJsonNode).getSchema(this.schemaJsonNode);
    }

    private JsonSchemaFactory getJsonSchemaFactory(JsonNode schema) {
        if (schema != null) {
            JsonNode metaSchema = schema.get("$schema");
            if (metaSchema != null) {
                String metaSchemaValue = metaSchema.textValue();
                if (isNotBlank(metaSchemaValue)) {
                    return getJsonSchemaFactory(metaSchemaValue);
                }
            }
        }
        return JsonSchemaFactory.getInstance(DEFAULT_JSON_SCHEMA_VERSION);
    }

    private JsonSchemaFactory getJsonSchemaFactory(String metaSchemaValue) {
        if (metaSchemaValue.contains("draft-03") || metaSchemaValue.contains("draft-04")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        } else if (metaSchemaValue.contains("draft-05") || metaSchemaValue.contains("draft-06")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V6);
        } else if (metaSchemaValue.contains("draft-07")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        } else if (metaSchemaValue.contains("draft/2019-09")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
        }
        return JsonSchemaFactory.getInstance(DEFAULT_JSON_SCHEMA_VERSION);
    }

    private JsonNode getSchemaJsonNode() {
        try {
            return OBJECT_MAPPER.readTree(this.schema);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception loading JSON Schema " + throwable.getMessage())
                    .setThrowable(throwable)
            );
            throw new RuntimeException("Unable to parse JSON schema", throwable);
        }
    }

    public String getSchema() {
        return schema;
    }

    private String addReferencesIntoSchema(String routePath, String mainSchemeFile, String... referenceFiles) {
        String combinedSchema = "";
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            JsonNode jsonSchema = objectMapper.readTree(FileReader.readFileFromClassPathOrPath(routePath + mainSchemeFile + ".json"));
            JsonNode definitions = jsonSchema.get("definitions");
            if (definitions instanceof ObjectNode) {
                for (String definitionName : referenceFiles) {
                    JsonNode definition = objectMapper.readTree(FileReader.readFileFromClassPathOrPath(routePath + definitionName + ".json"));
                    ((ObjectNode) definitions).set(
                        definitionName,
                        definition
                    );
                    if (definition != null && definition.get("definitions") != null) {
                        StreamSupport
                            .stream(Spliterators.spliteratorUnknownSize(definition.get("definitions").fields(), Spliterator.ORDERED), false)
                            .forEach(stringJsonNodeEntry -> ((ObjectNode) definitions).set(stringJsonNodeEntry.getKey(), stringJsonNodeEntry.getValue()));
                    }
                }
            }
            combinedSchema = ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(jsonSchema);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception loading JSON Schema " + throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
        return combinedSchema;
    }

    @Override
    public String isValid(String json) {
        return isValid(json, true);
    }

    public String isValid(String json, boolean addOpenAPISpecificationMessage) {
        String validationResult = "";
        if (isNotBlank(json)) {
            try {
                validationResult = formatProcessingReport(validator.validate(OBJECT_MAPPER.readTree(json)), addOpenAPISpecificationMessage);
            } catch (Throwable throwable) {
                if (isNotBlank(throwable.getMessage()) && throwable.getMessage().contains("Unknown MetaSchema")) {
                    validator = getJsonSchemaFactory(throwable.getMessage()).getSchema(this.schemaJsonNode);
                    return isValid(json, addOpenAPISpecificationMessage);
                }
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception validating JSON")
                        .setThrowable(throwable)
                );
                return throwable.getClass().getSimpleName() + " - " + throwable.getMessage();
            }
        }
        return validationResult;
    }

    private String formatProcessingReport(Set<ValidationMessage> validationMessages, boolean addOpenAPISpecificationMessage) {
        if (validationMessages.isEmpty()) {
            return "";
        } else {
            Set<String> extraMessages = new HashSet<>();
            Set<String> formattedMessages = validationMessages
                .stream()
                .map(validationMessage -> {
                    String validationMessageText = String.valueOf(validationMessage);
                    if (((validationMessageText.startsWith("$.httpRequest") && validationMessageText.contains(".body: ")) || validationMessageText.contains("$.body: "))
                    && !validationMessageText.contains("is not defined in the schema and the schema does not allow additional properties")) {
                        return StringUtils.substringBefore(validationMessageText, ":") + ": should match one of its valid types: " + FileReader.readFileFromClassPathOrPath("org/mockserver/model/schema/body.json")
                            .replaceAll("#/definitions/draft-07", "http://json-schema.org/draft-07/schema")
                            .replaceAll(NEW_LINE, NEW_LINE + "   ");
                    }
                    if (validationMessageText.contains(".specUrlOrPayload: is missing but it is required")) {
                        return StringUtils.substringBefore(validationMessageText, ":") + ": is missing, but is required, if specifying OpenAPI request matcher";
                    }
                    if (validationMessageText.startsWith("$.httpResponse.body: ")) {
                        return "$.httpResponse.body: should match one of its valid types: " + FileReader.readFileFromClassPathOrPath("org/mockserver/model/schema/bodyWithContentType.json")
                            .replaceAll(NEW_LINE, NEW_LINE + "   ");
                    }
                    if (validationMessageText.contains(".httpRequest") || RequestDefinition.class.equals(type)) {
                        if (validationMessageText.contains(".secure: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".keepAlive: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".method: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".path: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".pathParameters: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".queryStringParameters: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".body: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".headers: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".cookies: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".socketAddress: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".localAddress: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".remoteAddress: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".specUrlOrPayload: is not defined in the schema and the schema does not allow additional properties") ||
                            validationMessageText.contains(".operationId: is not defined in the schema and the schema does not allow additional properties")) {
                            return null;
                        }
                    }
                    if (validationMessageText.endsWith("cookies: object found, array expected")) {
                        return StringUtils.substringBefore(validationMessageText, ":") + ": invalid cookie format, the following are valid examples: " + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleRegexCookie\": \"^some +regex$\", " + NEW_LINE +
                            "         \"exampleNottedRegexCookie\": \"!notThisValue\", " + NEW_LINE +
                            "         \"exampleSimpleStringCookie\": \"simpleStringMatch\"" + NEW_LINE +
                            "     }" + NEW_LINE +
                            "  " + NEW_LINE +
                            "  or:" + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleNumberSchemaCookie\": {" + NEW_LINE +
                            "             \"type\": \"number\"" + NEW_LINE +
                            "         }, " + NEW_LINE +
                            "         \"examplePatternSchemaCookie\": {" + NEW_LINE +
                            "             \"type\": \"string\", " + NEW_LINE +
                            "             \"pattern\": \"^some regex$\"" + NEW_LINE +
                            "         }, " + NEW_LINE +
                            "         \"exampleFormatSchemaCookie\": {" + NEW_LINE +
                            "             \"type\": \"string\", " + NEW_LINE +
                            "             \"format\": \"ipv4\"" + NEW_LINE +
                            "         }" + NEW_LINE +
                            "     }";
                    }
                    if (validationMessageText.endsWith("headers: object found, array expected")) {
                        return StringUtils.substringBefore(validationMessageText, ":") + ": invalid header format, the following are valid examples: " + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleRegexHeader\": [" + NEW_LINE +
                            "             \"^some +regex$\"" + NEW_LINE +
                            "         ], " + NEW_LINE +
                            "         \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
                            "             \"!notThisValue\", " + NEW_LINE +
                            "             \"simpleStringMatch\"" + NEW_LINE +
                            "         ]" + NEW_LINE +
                            "     }" + NEW_LINE +
                            "  " + NEW_LINE +
                            "  or:" + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleSchemaHeader\": [" + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"number\"" + NEW_LINE +
                            "             }" + NEW_LINE +
                            "         ], " + NEW_LINE +
                            "         \"exampleMultiSchemaHeader\": [" + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"string\", " + NEW_LINE +
                            "                 \"pattern\": \"^some +regex$\"" + NEW_LINE +
                            "             }, " + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"string\", " + NEW_LINE +
                            "                 \"format\": \"ipv4\"" + NEW_LINE +
                            "             }" + NEW_LINE +
                            "         ]" + NEW_LINE +
                            "     }";
                    }
                    if (validationMessageText.endsWith("pathParameters: object found, array expected") || validationMessageText.endsWith("queryStringParameters: object found, array expected")) {
                        return StringUtils.substringBefore(validationMessageText, ":") + ": invalid parameter format, the following are valid examples: " + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleRegexParameter\": [" + NEW_LINE +
                            "             \"^some +regex$\"" + NEW_LINE +
                            "         ], " + NEW_LINE +
                            "         \"exampleNottedAndSimpleStringParameter\": [" + NEW_LINE +
                            "             \"!notThisValue\", " + NEW_LINE +
                            "             \"simpleStringMatch\"" + NEW_LINE +
                            "         ]" + NEW_LINE +
                            "     }" + NEW_LINE +
                            "  " + NEW_LINE +
                            "  or:" + NEW_LINE +
                            "  " + NEW_LINE +
                            "     {" + NEW_LINE +
                            "         \"exampleSchemaParameter\": [" + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"number\"" + NEW_LINE +
                            "             }" + NEW_LINE +
                            "         ], " + NEW_LINE +
                            "         \"exampleMultiSchemaParameter\": [" + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"string\", " + NEW_LINE +
                            "                 \"pattern\": \"^some +regex$\"" + NEW_LINE +
                            "             }, " + NEW_LINE +
                            "             {" + NEW_LINE +
                            "                 \"type\": \"string\", " + NEW_LINE +
                            "                 \"format\": \"ipv4\"" + NEW_LINE +
                            "             }" + NEW_LINE +
                            "         ]" + NEW_LINE +
                            "     }";
                    }
                    if (validationMessageText.startsWith("$.http") && validationMessageText.endsWith(": is missing but it is required")) {
                        extraMessages.add("oneOf of the following must be specified [" +
                            "httpError, " +
                            "httpForward, " +
                            "httpForwardClassCallback, " +
                            "httpForwardObjectCallback, " +
                            "httpForwardTemplate, " +
                            "httpOverrideForwardedRequest, " +
                            "httpResponse, " +
                            "httpResponseClassCallback, " +
                            "httpResponseObjectCallback, " +
                            "httpResponseTemplate" +
                            "]");
                        return StringUtils.substringBefore(validationMessageText, ":") + ": is missing, but is required, if specifying action of type " + StringUtils.substringBefore(StringUtils.substringAfter(validationMessageText, "$.http"), ":");
                    }
                    return validationMessageText;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            formattedMessages.addAll(extraMessages);
            List<String> validationMessageTexts = formattedMessages
                .stream()
                .filter(formattedMessage -> !formattedMessage.endsWith("object expected") || !formattedMessages.contains(formattedMessage.replace("object expected", "string expected")))
                .sorted().collect(Collectors.toList());
            return validationMessageTexts.size() + " error" + (validationMessageTexts.size() > 1 ? "s" : "") + ":" + NEW_LINE
                + " - " + Joiner.on(NEW_LINE + " - ").join(validationMessageTexts) +
                (addOpenAPISpecificationMessage ? NEW_LINE + NEW_LINE + OPEN_API_SPECIFICATION_URL : "");
        }
    }

}
