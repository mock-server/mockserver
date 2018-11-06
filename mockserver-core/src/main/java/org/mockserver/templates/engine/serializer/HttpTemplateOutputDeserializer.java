package org.mockserver.templates.engine.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;

/**
 * @author jamesdbloom
 */
public class HttpTemplateOutputDeserializer {

    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final MockServerLogger mockServerLogger;
    private JsonSchemaHttpRequestValidator httpRequestValidator;
    private JsonSchemaHttpResponseValidator httpResponseValidator;

    public HttpTemplateOutputDeserializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        httpRequestValidator = new JsonSchemaHttpRequestValidator(mockServerLogger);
        httpResponseValidator = new JsonSchemaHttpResponseValidator(mockServerLogger);
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
                mockServerLogger.error(request, "validation failed:{}" + StringUtils.uncapitalize(dtoClass.getSimpleName()) + ":{}", validationErrors, json);
            }
        } catch (Exception e) {
            mockServerLogger.error(request, e, "Exception transforming json:{}", json);
        }
        return result;
    }
}
