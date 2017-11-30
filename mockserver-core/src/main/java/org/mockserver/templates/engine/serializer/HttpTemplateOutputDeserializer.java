package org.mockserver.templates.engine.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.DTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.logging.LogFormatter;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class HttpTemplateOutputDeserializer {

    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(HttpTemplateOutputDeserializer.class);
    private static LogFormatter logFormatter = new LogFormatter(logger);
    private JsonSchemaHttpRequestValidator httpRequestValidator = new JsonSchemaHttpRequestValidator();
    private JsonSchemaHttpResponseValidator httpResponseValidator = new JsonSchemaHttpResponseValidator();

    public <T> T deserializer(String json, Class<? extends DTO<T>> dtoClass) {
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
                logFormatter.errorLog("Validation failed:{}for:{}against schema:{}", validationErrors, json, schema);
            }
        } catch (Exception e) {
            logFormatter.errorLog(e, "Exception transforming json:{}", json);
        }
        return result;
    }
}
