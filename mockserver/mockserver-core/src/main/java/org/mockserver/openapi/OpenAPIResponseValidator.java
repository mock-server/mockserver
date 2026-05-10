package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.mockserver.openapi.OpenAPIParser.mapOperations;

@SuppressWarnings("rawtypes")
public class OpenAPIResponseValidator {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public static List<String> validate(String specUrlOrPayload, String operationId, HttpResponse response, MockServerLogger logger) {
        List<String> errors = new ArrayList<>();
        try {
            OpenAPI openAPI = buildOpenAPI(specUrlOrPayload, logger);
            Optional<Pair<String, Operation>> operationPair = openAPI
                .getPaths()
                .values()
                .stream()
                .flatMap(pathItem -> mapOperations(pathItem).stream())
                .filter(pair -> pair.getRight().getOperationId().equals(operationId))
                .findFirst();

            if (!operationPair.isPresent()) {
                errors.add("operation " + operationId + " not found in OpenAPI spec");
                return errors;
            }

            Operation operation = operationPair.get().getRight();
            String statusCode = String.valueOf(response.getStatusCode() != null ? response.getStatusCode() : 200);
            ApiResponse apiResponse = null;

            if (operation.getResponses() != null) {
                apiResponse = operation.getResponses().get(statusCode);
                if (apiResponse == null) {
                    apiResponse = operation.getResponses().get("default");
                }
            }

            if (apiResponse == null) {
                errors.add("response status code " + statusCode + " not defined in OpenAPI spec for operation " + operationId);
                return errors;
            }

            validateResponseBody(apiResponse, response, logger, errors);
            validateResponseHeaders(apiResponse, response, logger, errors);
        } catch (Throwable throwable) {
            errors.add("OpenAPI response validation error: " + throwable.getMessage());
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static void validateResponseBody(ApiResponse apiResponse, HttpResponse response, MockServerLogger logger, List<String> errors) {
        if (apiResponse.getContent() == null || apiResponse.getContent().isEmpty()) {
            return;
        }

        String bodyString = response.getBodyAsString();
        if (isBlank(bodyString)) {
            return;
        }

        String contentType = response.getFirstHeader("content-type");
        MediaType mediaType = null;
        if (isNotBlank(contentType)) {
            String baseContentType = contentType.contains(";") ? contentType.substring(0, contentType.indexOf(";")).trim() : contentType.trim();
            mediaType = apiResponse.getContent().get(baseContentType);
        }
        if (mediaType == null) {
            mediaType = apiResponse.getContent().get("application/json");
        }
        if (mediaType == null) {
            mediaType = apiResponse.getContent().get("*/*");
        }
        if (mediaType == null) {
            Map.Entry<String, MediaType> firstEntry = apiResponse.getContent().entrySet().iterator().next();
            mediaType = firstEntry.getValue();
        }

        Schema schema = mediaType.getSchema();
        if (schema == null) {
            return;
        }

        try {
            String schemaJson = OBJECT_MAPPER.writeValueAsString(schema);
            JsonSchemaValidator validator = new JsonSchemaValidator(logger, schemaJson);
            String validationResult = validator.isValid(bodyString, false);
            if (isNotBlank(validationResult)) {
                errors.add("response body validation error: " + validationResult);
            }
        } catch (Throwable throwable) {
            errors.add("failed to validate response body against schema: " + throwable.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateResponseHeaders(ApiResponse apiResponse, HttpResponse response, MockServerLogger logger, List<String> errors) {
        if (apiResponse.getHeaders() == null || apiResponse.getHeaders().isEmpty()) {
            return;
        }

        for (Map.Entry<String, Header> headerEntry : apiResponse.getHeaders().entrySet()) {
            String headerName = headerEntry.getKey();
            Header headerDef = headerEntry.getValue();

            String headerValue = response.getFirstHeader(headerName);
            if (isBlank(headerValue)) {
                if (headerDef.getRequired() != null && headerDef.getRequired()) {
                    errors.add("required response header " + headerName + " not found in response");
                }
                continue;
            }

            Schema headerSchema = headerDef.getSchema();
            if (headerSchema == null) {
                continue;
            }

            try {
                String schemaJson = OBJECT_MAPPER.writeValueAsString(headerSchema);
                JsonSchemaValidator validator = new JsonSchemaValidator(logger, schemaJson);
                String jsonValue = headerValue;
                if ("string".equals(headerSchema.getType())) {
                    jsonValue = OBJECT_MAPPER.writeValueAsString(headerValue);
                } else if ("integer".equals(headerSchema.getType()) || "number".equals(headerSchema.getType())) {
                    // leave as-is, numbers are valid JSON
                } else if ("boolean".equals(headerSchema.getType())) {
                    // leave as-is, booleans are valid JSON
                } else {
                    jsonValue = OBJECT_MAPPER.writeValueAsString(headerValue);
                }
                String validationResult = validator.isValid(jsonValue, false);
                if (isNotBlank(validationResult)) {
                    errors.add("response header " + headerName + " validation error: " + validationResult);
                }
            } catch (Throwable throwable) {
                errors.add("failed to validate response header " + headerName + " against schema: " + throwable.getMessage());
            }
        }
    }

}
