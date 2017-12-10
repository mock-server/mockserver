package org.mockserver.templates.engine.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.DTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;

/**
 * @author jamesdbloom
 */
public class HttpTemplateOutputDeserializer {

    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final LoggingFormatter logFormatter;
    private JsonSchemaHttpRequestValidator httpRequestValidator = new JsonSchemaHttpRequestValidator();
    private JsonSchemaHttpResponseValidator httpResponseValidator = new JsonSchemaHttpResponseValidator();

    public HttpTemplateOutputDeserializer(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
    }

    public <T> T deserializer(HttpRequest request, String json, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        try {
            String validationErrors = "", schema = "";
            if (dtoClass.isAssignableFrom(HttpResponseDTO.class)) {
                validationErrors = httpResponseValidator.isValid(json);
                schema = httpResponseValidator.getSchema();
            } else if (dtoClass.isAssignableFrom(HttpRequestDTO.class)) {
                validationErrors = httpRequestValidator.isValid(json);
                schema = httpRequestValidator.getSchema();
            }
            if (StringUtils.isEmpty(validationErrors)) {
                result = objectMapper.readValue(json, dtoClass).buildObject();
            } else {
                logFormatter.errorLog(request, "Validation failed:{}for:{}against schema:{}", validationErrors, json, schema);
            }
        } catch (Exception e) {
            logFormatter.errorLog(request, e, "Exception transforming json:{}", json);
        }
        return result;
    }
}
