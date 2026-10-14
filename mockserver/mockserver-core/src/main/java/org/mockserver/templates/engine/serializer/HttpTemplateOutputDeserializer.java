package org.mockserver.templates.engine.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;
import org.slf4j.event.Level;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.uncapitalize;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATION_FAILED;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator.jsonSchemaHttpRequestValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator.jsonSchemaHttpResponseValidator;

/**
 * @author jamesdbloom
 */
public class HttpTemplateOutputDeserializer {

    private static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static final ThreadLocal<Consumer<String>> INVALID_OUTPUT_LISTENER = new ThreadLocal<>();
    private final MockServerLogger mockServerLogger;
    private JsonSchemaHttpRequestValidator httpRequestValidator;
    private JsonSchemaHttpResponseValidator httpResponseValidator;

    public HttpTemplateOutputDeserializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        httpRequestValidator = jsonSchemaHttpRequestValidator(mockServerLogger);
        httpResponseValidator = jsonSchemaHttpResponseValidator(mockServerLogger);
    }

    /**
     * Runs {@code render} and, when a template it renders on this thread produces output that is not a valid
     * request or response, also passes the reason to {@code invalidOutput}. The output is still logged and still
     * deserialises to null, so the render's result is unchanged.
     */
    public static <T> T reportingInvalidOutput(Consumer<String> invalidOutput, Supplier<T> render) {
        Consumer<String> enclosing = INVALID_OUTPUT_LISTENER.get();
        INVALID_OUTPUT_LISTENER.set(invalidOutput);
        try {
            return render.get();
        } finally {
            if (enclosing != null) {
                INVALID_OUTPUT_LISTENER.set(enclosing);
            } else {
                INVALID_OUTPUT_LISTENER.remove();
            }
        }
    }

    private static void reportInvalidOutput(String reason) {
        Consumer<String> listener = INVALID_OUTPUT_LISTENER.get();
        if (listener != null) {
            listener.accept(reason);
        }
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
            if (isEmpty(validationErrors)) {
                result = objectMapper.readValue(json, dtoClass).buildObject();
            } else {
                if (validationErrors.contains("statusCode: must have a")) {
                    validationErrors += NEW_LINE + NEW_LINE + "the template rendered a statusCode outside 100 to 999; an HTTP status line carries a three-digit code, so the rendered response was not used";
                }
                // The template rendered output that is not a valid HttpResponse/HttpRequest. Classify this
                // as TEMPLATE_GENERATION_FAILED (not a generic ERROR) and carry both the validation errors
                // and the offending rendered output, correlated to the request. Returning null makes the
                // action degrade to a 404 fallback; the 404 remains the correct client response (there is
                // no valid response to return) and is intentionally NOT changed here — this is an
                // observability fix only. Without this, the failure was invisible: the fallback 404 was
                // logged as a success-looking EXPECTATION_RESPONSE and the real error as an undistinguished
                // ERROR line.
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(TEMPLATE_GENERATION_FAILED)
                        .setLogLevel(Level.ERROR)
                        .setHttpRequest(request)
                        .setMessageFormat("template generated output that failed " + uncapitalize(dtoClass.getSimpleName()) + " validation:{}generated output:{}")
                        .setArguments(validationErrors, json)
                );
                reportInvalidOutput(validationErrors);
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(TEMPLATE_GENERATION_FAILED)
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception transforming template generated output into a valid response:{}generated output:{}")
                    .setArguments(e.getMessage(), json)
                    .setThrowable(e)
            );
            reportInvalidOutput("the output could not be read: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        return result;
    }
}
