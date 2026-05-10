package org.mockserver.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;
import static org.mockserver.openapi.OpenAPIParser.mapOperations;

@SuppressWarnings("rawtypes")
public class OpenAPIRequestValidator {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public static List<String> validate(String specUrlOrPayload, HttpRequest request, MockServerLogger logger) {
        List<String> errors = new ArrayList<>();
        try {
            OpenAPI openAPI = buildOpenAPI(specUrlOrPayload, logger);
            String requestPath = request.getPath() != null ? request.getPath().getValue() : "/";
            String requestMethod = request.getMethod() != null ? request.getMethod().getValue().toLowerCase() : "get";

            Optional<Operation> matchedOperation = findMatchingOperation(openAPI, requestPath, requestMethod);
            if (!matchedOperation.isPresent()) {
                errors.add("no operation found matching " + requestMethod.toUpperCase() + " " + requestPath + " in OpenAPI spec");
                return errors;
            }

            Operation operation = matchedOperation.get();
            validateRequestBody(operation, request, logger, errors);
        } catch (Throwable throwable) {
            errors.add("OpenAPI request validation error: " + throwable.getMessage());
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Operation> findMatchingOperation(OpenAPI openAPI, String requestPath, String requestMethod) {
        return openAPI
            .getPaths()
            .entrySet()
            .stream()
            .filter(entry -> pathMatches(entry.getKey(), requestPath))
            .flatMap(entry -> mapOperations(entry.getValue()).stream())
            .filter(pair -> pair.getLeft().equalsIgnoreCase(requestMethod))
            .map(Pair::getRight)
            .findFirst();
    }

    private static boolean pathMatches(String templatePath, String actualPath) {
        StringBuilder regex = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{[^}]+}").matcher(templatePath);
        int lastEnd = 0;
        while (matcher.find()) {
            regex.append(java.util.regex.Pattern.quote(templatePath.substring(lastEnd, matcher.start())));
            regex.append("[^/]+");
            lastEnd = matcher.end();
        }
        regex.append(java.util.regex.Pattern.quote(templatePath.substring(lastEnd)));
        return actualPath.matches(regex.toString());
    }

    @SuppressWarnings("unchecked")
    private static void validateRequestBody(Operation operation, HttpRequest request, MockServerLogger logger, List<String> errors) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null) {
            return;
        }

        String bodyString = request.getBodyAsString();
        if (isBlank(bodyString)) {
            if (requestBody.getRequired() != null && requestBody.getRequired()) {
                errors.add("request body is required but was empty");
            }
            return;
        }

        if (requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return;
        }

        String contentType = request.getFirstHeader("content-type");
        MediaType mediaType = null;
        if (isNotBlank(contentType)) {
            String baseContentType = contentType.contains(";") ? contentType.substring(0, contentType.indexOf(";")).trim() : contentType.trim();
            mediaType = requestBody.getContent().get(baseContentType);
        }
        if (mediaType == null) {
            mediaType = requestBody.getContent().get("application/json");
        }
        if (mediaType == null) {
            mediaType = requestBody.getContent().get("*/*");
        }
        if (mediaType == null && !requestBody.getContent().isEmpty()) {
            mediaType = requestBody.getContent().values().iterator().next();
        }

        if (mediaType == null) {
            return;
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
                errors.add("request body validation error: " + validationResult);
            }
        } catch (Throwable throwable) {
            errors.add("failed to validate request body against schema: " + throwable.getMessage());
        }
    }
}
