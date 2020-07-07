package org.mockserver.validator.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.validator.Validator;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    public static final String OPEN_API_SPECIFICATION_URL = "See: https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.11.x for OpenAPI Specification";
    private static final Map<String, String> schemaCache = new ConcurrentHashMap<>();
    private final MockServerLogger mockServerLogger;
    private final String schema;
    private final JsonNode schemaJsonNode;
    private final String mainSchemeFile;
    private final JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();
    private final static ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        if (schema.trim().endsWith(".json")) {
            this.schema = FileReader.readFileFromClassPathOrPath(schema);
        } else if (schema.trim().endsWith("}")) {
            this.schema = schema;
        } else {
            throw new IllegalArgumentException("Schema must either be a path reference to a *.json file or a json string");
        }
        this.mainSchemeFile = null;
        this.schemaJsonNode = getSchemaJsonNode();
    }

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String routePath, String mainSchemeFile, String... referenceFiles) {
        this.mockServerLogger = mockServerLogger;
        if (!schemaCache.containsKey(mainSchemeFile)) {
            schemaCache.put(mainSchemeFile, addReferencesIntoSchema(routePath, mainSchemeFile, referenceFiles));
        }
        this.schema = schemaCache.get(mainSchemeFile);
        this.mainSchemeFile = mainSchemeFile;
        this.schemaJsonNode = getSchemaJsonNode();
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
            return null;
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
                    ((ObjectNode) definitions).set(
                        definitionName,
                        objectMapper.readTree(FileReader.readFileFromClassPathOrPath(routePath + definitionName + ".json"))
                    );
                }
            }
            combinedSchema = ObjectMapperFactory
                .createObjectMapper(true)
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

                ProcessingReport processingReport = validator
                    .validate(
                        schemaJsonNode,
                        OBJECT_MAPPER.readTree(json),
                        true
                    );

                if (!processingReport.isSuccess()) {
                    validationResult = formatProcessingReport(processingReport, addOpenAPISpecificationMessage);
                }
            } catch (Throwable throwable) {
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

    private String formatProcessingReport(ProcessingReport processingMessages, boolean addOpenAPISpecificationMessage) {
        List<String> validationErrors = new ArrayList<>();
        for (ProcessingMessage processingMessage : processingMessages) {
            JsonNode processingMessageJson = processingMessage.asJson();
            JsonNode instanceJson = processingMessageJson.get("instance");
            JsonNode schemaJson = processingMessageJson.get("schema");
            JsonNode reports = processingMessageJson.get("reports");
            String fieldPointer = pointerValue(instanceJson).replaceAll("\"", "");
            String schemaPointer = removeDefinitionPrefix(pointerValue(schemaJson));
            if (isErrorForField(reports, fieldPointer, "/headers")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/headers") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleRegexHeader\": [" + NEW_LINE +
                    "           \"^some +regex$\"" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
                    "           \"!notThisValue\", " + NEW_LINE +
                    "           \"simpleStringMatch\"" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE + NEW_LINE +
                    "or:" + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleSchemaHeader\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"number\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                    "           }, " + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"format\": \"ipv4\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (isErrorForField(reports, fieldPointer, "/pathParameters")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/pathParameters") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleRegexParameter\": [" + NEW_LINE +
                    "           \"^some +regex$\"" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleNottedAndSimpleStringParameter\": [" + NEW_LINE +
                    "           \"!notThisValue\", " + NEW_LINE +
                    "           \"simpleStringMatch\"" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE + NEW_LINE +
                    "or:" + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleSchemaParameter\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"number\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleMultiSchemaParameter\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                    "           }, " + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"format\": \"ipv4\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (isErrorForField(reports, fieldPointer, "/queryStringParameters")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/queryStringParameters") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleRegexParameter\": [" + NEW_LINE +
                    "           \"^some +regex$\"" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleNottedAndSimpleStringParameter\": [" + NEW_LINE +
                    "           \"!notThisValue\", " + NEW_LINE +
                    "           \"simpleStringMatch\"" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE + NEW_LINE +
                    "or:" + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleSchemaParameter\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"number\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ], " + NEW_LINE +
                    "       \"exampleMultiSchemaParameter\": [" + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                    "           }, " + NEW_LINE +
                    "           {" + NEW_LINE +
                    "               \"type\": \"string\", " + NEW_LINE +
                    "               \"format\": \"ipv4\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "       ]" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (isErrorForField(reports, fieldPointer, "/cookies")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/cookies") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleRegexCookie\": \"^some +regex$\", " + NEW_LINE +
                    "       \"exampleNottedRegexCookie\": \"!notThisValue\", " + NEW_LINE +
                    "       \"exampleSimpleStringCookie\": \"simpleStringMatch\"" + NEW_LINE +
                    "   }" + NEW_LINE + NEW_LINE +
                    "or:" + NEW_LINE + NEW_LINE +
                    "   {" + NEW_LINE +
                    "       \"exampleNumberSchemaCookie\": {" + NEW_LINE +
                    "           \"type\": \"number\"" + NEW_LINE +
                    "       }, " + NEW_LINE +
                    "       \"examplePatternSchemaCookie\": {" + NEW_LINE +
                    "           \"type\": \"string\", " + NEW_LINE +
                    "           \"pattern\": \"^some +regex$\"" + NEW_LINE +
                    "       }, " + NEW_LINE +
                    "       \"exampleFormatSchemaCookie\": {" + NEW_LINE +
                    "           \"type\": \"string\", " + NEW_LINE +
                    "           \"format\": \"ipv4\"" + NEW_LINE +
                    "       }" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (isErrorForField(reports, fieldPointer, "/body") && !schemaPointer.contains("bodyWithContentType")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/body") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"BINARY\"," + NEW_LINE +
                    "     \"base64Bytes\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }, " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"JSON\"," + NEW_LINE +
                    "     \"json\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"," + NEW_LINE +
                    "     \"matchType\": \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"JSON_SCHEMA\"," + NEW_LINE +
                    "     \"jsonSchema\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"JSON_PATH\"," + NEW_LINE +
                    "     \"jsonPath\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                    "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"REGEX\"," + NEW_LINE +
                    "     \"regex\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"STRING\"," + NEW_LINE +
                    "     \"string\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"XML\"," + NEW_LINE +
                    "     \"xml\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"XML_SCHEMA\"," + NEW_LINE +
                    "     \"xmlSchema\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"not\": false," + NEW_LINE +
                    "     \"type\": \"XPATH\"," + NEW_LINE +
                    "     \"xpath\": \"\"" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (isErrorForField(reports, fieldPointer, "/body") && schemaPointer.contains("bodyWithContentType")) {
                validationErrors.add("field: \"" + deepFieldName(reports, fieldPointer, "/body") + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"BINARY\"," + NEW_LINE +
                    "     \"base64Bytes\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }, " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"JSON\"," + NEW_LINE +
                    "     \"json\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                    "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"STRING\"," + NEW_LINE +
                    "     \"string\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"XML\"," + NEW_LINE +
                    "     \"xml\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }" + NEW_LINE);
            }
            if (String.valueOf(processingMessageJson.get("keyword")).contains("oneOf")) {
                StringBuilder oneOfErrorMessage = new StringBuilder("oneOf of the following must be specified ");
                if (fieldPointer.isEmpty() && isNotBlank(mainSchemeFile)) {
                    if (mainSchemeFile.contains("expectation")) {
                        validationErrors.add(
                            oneOfErrorMessage
                                .append(Arrays.asList(
                                    "\"httpResponse\"",
                                    "\"httpResponseTemplate\"",
                                    "\"httpResponseObjectCallback\"",
                                    "\"httpResponseClassCallback\"",
                                    "\"httpForward\"",
                                    "\"httpForwardTemplate\"",
                                    "\"httpForwardObjectCallback\"",
                                    "\"httpForwardClassCallback\"",
                                    "\"httpOverrideForwardedRequest\"",
                                    "\"httpError\""
                                ))
                                .append(" but found ")
                                .append(processingMessageJson.get("matched"))
                                .append(" without errors")
                                .toString()
                        );
                    } else if (mainSchemeFile.contains("requestDefinition")) {
                        validationErrors.add(
                            oneOfErrorMessage
                                .append(Arrays.asList(
                                    "\"httpRequest\"",
                                    "\"openAPIDefinition\""
                                ))
                                .append(" but found ")
                                .append(processingMessageJson.get("matched"))
                                .append(" without errors")
                                .toString()
                        );
                    }
                }
            }
            if (fieldPointer.endsWith("/times") && processingMessage.toString().contains("has properties which are not allowed by the schema") && String.valueOf(schemaJson).contains("verificationTimes")) {
                validationErrors.add("field: \"" + fieldPointer + (isNotBlank(schemaPointer) ? "\" for schema: \"" + schemaPointer : "") + "\" has error: \"" + processingMessage.getMessage() + ", allowed fields are [\"atLeast\", \"atMost\"]\"");
            }
            if (reports != null) {
                validationErrors.addAll(extractMessage(reports));
            } else {
                validationErrors.addAll(extractMessage(processingMessageJson));
            }
        }
        validationErrors.sort(String::compareToIgnoreCase);
        return validationErrors.size() + " error" + (validationErrors.size() > 1 ? "s" : "") + ":" + NEW_LINE
            + " - " + Joiner.on(NEW_LINE + " - ").join(validationErrors) +
            (addOpenAPISpecificationMessage ? NEW_LINE + NEW_LINE + OPEN_API_SPECIFICATION_URL : "");
    }

    private boolean isErrorForField(JsonNode reports, String fieldPointer, String fieldName) {
        return fieldPointer.endsWith(fieldName) // http response
            || (fieldPointer.contains("/httpRequest") && reports.has("/definitions/requestDefinition/oneOf/0") && stream(reports.get("/definitions/requestDefinition/oneOf/0").iterator()).anyMatch(jsonNode -> pointerValue(jsonNode.get("instance")).endsWith(fieldName))); // http request(s)
    }

    private String deepFieldName(JsonNode reports, String fieldPointer, String fieldName) {
        if (fieldPointer.endsWith(fieldName)) {
            // http response
            return fieldPointer;
        } else if (fieldPointer.contains("/httpRequest") && reports.has("/definitions/requestDefinition/oneOf/0") && stream(reports.get("/definitions/requestDefinition/oneOf/0").iterator()).anyMatch(jsonNode -> pointerValue(jsonNode.get("instance")).endsWith(fieldName))) {
            // http request
            return stream(reports.get("/definitions/requestDefinition/oneOf/0").iterator()).filter(jsonNode -> pointerValue(jsonNode.get("instance")).endsWith(fieldName)).findFirst().map(instanceNode -> pointerValue(instanceNode.get("instance"))).orElse("");
        } else {
            return "";
        }
    }

    private String removeDefinitionPrefix(String text) {
        return StringUtils.remove(text, "/definitions/");
    }

    private String pointerValue(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.get("pointer") != null && isNotBlank(jsonNode.get("pointer").asText()) ? jsonNode.get("pointer").asText() : "";
    }

    public static <T> Stream<T> stream(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    public Set<String> extractMessage(JsonNode reports) {
        Set<String> messages = new HashSet<>();
        if (reports != null) {
            // if object
            stream(reports.fields())
                .forEach(field -> {
                    if ("message".equals(field.getKey())) {
                        String fieldName = pointerValue(reports.get("instance"));
                        String schemaName = pointerValue(reports.get("schema"));
                        if (field.getValue() != null) {
                            boolean fieldNotBlank = isNotBlank(fieldName);
                            boolean schemaNotBlank = isNotBlank(schemaName);
                            messages.add((fieldNotBlank ? "field: \"" + fieldName + "\"" : "") + (schemaNotBlank ? (fieldNotBlank ? " for " : "") + "schema: \"" + removeDefinitionPrefix(schemaName) + "\"" : "") + (fieldNotBlank || schemaNotBlank ? " has error: \"" : "") + field.getValue().asText() + (fieldNotBlank || schemaNotBlank ? "\"" : ""));
                        }
                    } else if (field.getValue() != null && (field.getValue().isArray() || field.getValue().isObject())) {
                        messages.addAll(extractMessage(field.getValue()));
                    }
                });
            // if array
            stream(reports.iterator())
                .forEach(node -> messages.addAll(extractMessage(node)));
        }
        return messages;
    }
}
