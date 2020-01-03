package org.mockserver.templates.engine.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;
import org.slf4j.event.Level;

import static org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator.jsonSchemaHttpRequestValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator.jsonSchemaHttpResponseValidator;

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
        httpRequestValidator = jsonSchemaHttpRequestValidator(mockServerLogger);
        httpResponseValidator = jsonSchemaHttpResponseValidator(mockServerLogger);
    }

    public <T> T deserializer(HttpRequest request, String json, Class<? extends DTO<T>> dtoClass) {
        T result = null;
        try {
            String validationErrors = "";
            if (dtoClass.isAssignableFrom(HttpResponseDTO.class)) {
                validationErrors = httpResponseValidator.isValid(json);
            } else if (dtoClass.isAssignableFrom(HttpRequestDTO.class)) {
                validationErrors = httpRequestValidator.isValid(json);
            }
            if (StringUtils.isEmpty(validationErrors)) {
                result = objectMapper.readValue(json, dtoClass).buildObject();
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setHttpRequest(request)
                        .setMessageFormat("validation failed:{}" + StringUtils.uncapitalize(dtoClass.getSimpleName()) + ":{}")
                        .setArguments(validationErrors, json)
                );
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception transforming json:{}")
                    .setArguments(json)
                    .setThrowable(e)
            );
        }
        return result;
    }
}
