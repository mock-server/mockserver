package org.mockserver.validator.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.validator.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    private final String schema;
    private final JsonValidator validator = JsonSchemaFactory.byDefault().getValidator();
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final MockServerLogger mockServerLogger;

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        if (schema.trim().endsWith(".json")) {
            this.schema = FileReader.readFileFromClassPathOrPath(schema);
        } else if (schema.trim().endsWith("}")) {
            this.schema = schema;
        } else {
            throw new IllegalArgumentException("Schema must either be a path reference to a *.json file or a json string");
        }
    }

    public JsonSchemaValidator(MockServerLogger mockServerLogger, String routePath, String mainSchemeFile, String... referenceFiles) {
        this.mockServerLogger = mockServerLogger;
        this.schema = addReferencesIntoSchema(routePath, mainSchemeFile, referenceFiles);
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
            if (definitions != null && definitions instanceof ObjectNode) {
                for (String definitionName : referenceFiles) {
                    ((ObjectNode) definitions).set(
                        definitionName,
                        objectMapper.readTree(FileReader.readFileFromClassPathOrPath(routePath + definitionName + ".json"))
                    );
                }
            }
            combinedSchema = ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonSchema);
        } catch (Exception e) {
            mockServerLogger.error("Exception loading JSON Schema for Exceptions", e);
        }
        return combinedSchema;
    }

    @Override
    public String isValid(String json) {
        String validationResult = "";
        if (!Strings.isNullOrEmpty(json)) {
            try {

                ProcessingReport processingReport = validator
                    .validate(
                        objectMapper.readTree(schema),
                        objectMapper.readTree(json),
                        true
                    );

                if (!processingReport.isSuccess()) {
                    validationResult = formatProcessingReport(processingReport);
                }
            } catch (Exception e) {
                mockServerLogger.info("Exception validating JSON", e);
                return e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }
        return validationResult;
    }

    private String formatProcessingReport(ProcessingReport validate) {
        List<String> validationErrors = new ArrayList<String>();
        for (ProcessingMessage processingMessage : validate) {
            String fieldPointer = "";
            if (processingMessage.asJson().get("instance") != null && processingMessage.asJson().get("instance").get("pointer") != null) {
                fieldPointer = String.valueOf(processingMessage.asJson().get("instance").get("pointer")).replaceAll("\"", "");
            }
            if (fieldPointer.endsWith("/headers")) {
                validationErrors.add("for field \"" + fieldPointer + "\" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : {" + NEW_LINE +
                    "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "    }" + NEW_LINE + NEW_LINE +
                    "   or:" + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : [" + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    ]");
            } else if (fieldPointer.endsWith("/queryStringParameters")) {
                validationErrors.add("for field \"" + fieldPointer + "\" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : {" + NEW_LINE +
                    "        \"exampleParameterName\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
                    "        \"exampleMultiValuedParameterName\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
                    "    }" + NEW_LINE + NEW_LINE +
                    "   or:" + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : [" + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleParameterName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleMultiValuedParameterName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    ]");
            } else if (fieldPointer.endsWith("/cookies")) {
                validationErrors.add("for field \"" + fieldPointer + "\" only one of the following example formats is allowed: " + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : {" + NEW_LINE +
                    "        \"exampleCookieNameOne\" : \"exampleCookieValueOne\"" + NEW_LINE +
                    "        \"exampleCookieNameTwo\" : \"exampleCookieValueTwo\"" + NEW_LINE +
                    "    }" + NEW_LINE + NEW_LINE +
                    "   or:" + NEW_LINE + NEW_LINE +
                    "    \"" + fieldPointer + "\" : [" + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleCookieNameOne\"," + NEW_LINE +
                    "            \"values\" : \"exampleCookieValueOne\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleCookieNameTwo\"," + NEW_LINE +
                    "            \"values\" : \"exampleCookieValueTwo\"" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    ]");
            } else if (fieldPointer.endsWith("/body")) {
                validationErrors.add("for field \"" + fieldPointer + "\" a plain string or one of the following example bodies must be specified " + NEW_LINE +
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
                    "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                    "     \"parameters\": \"TO DO\"" + NEW_LINE +
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
                    "   }");
            } else if (String.valueOf(processingMessage.asJson().get("keyword")).equals("\"oneOf\"")) {
                StringBuilder oneOfErrorMessage = new StringBuilder("oneOf of the following must be specified ");
                if (fieldPointer.isEmpty()) {
                    oneOfErrorMessage.append(Arrays.asList(
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
                    ));
                } else {
                    for (JsonNode jsonNode : processingMessage.asJson().get("reports")) {
                        if (jsonNode.get(0) != null && jsonNode.get(0).get("required") != null && jsonNode.get(0).get("required").get(0) != null) {
                            oneOfErrorMessage.append(String.valueOf(jsonNode.get(0).get("required").get(0))).append(" ");
                        }
                    }
                }
                oneOfErrorMessage.append(" but ").append(String.valueOf(processingMessage.asJson().get("matched"))).append(" found");
                validationErrors.add(oneOfErrorMessage.toString() + (fieldPointer.isEmpty() ? "" : " for field \"" + fieldPointer + "\""));
            } else {
                validationErrors.add(processingMessage.getMessage() + (fieldPointer.isEmpty() ? "" : " for field \"" + fieldPointer + "\""));
            }
        }
        return validationErrors.size() + " error" + (validationErrors.size() > 1 ? "s" : "") + ":" + NEW_LINE + " - " + Joiner.on(NEW_LINE + " - ").join(validationErrors);
    }
}
