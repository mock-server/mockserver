package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpRequestPrettyPrintedDTO;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator.jsonSchemaHttpRequestValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class HttpRequestSerializer implements Serializer<HttpRequest> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaHttpRequestValidator httpRequestValidator;

    public HttpRequestSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    private JsonSchemaHttpRequestValidator getValidator() {
        if (httpRequestValidator == null) {
            httpRequestValidator = jsonSchemaHttpRequestValidator(mockServerLogger);
        }
        return httpRequestValidator;
    }

    public String serialize(HttpRequest httpRequest) {
        return serialize(false, httpRequest);
    }

    public String serialize(boolean prettyPrint, HttpRequest httpRequest) {
        try {
            if (prettyPrint) {
                return objectWriter.writeValueAsString(new HttpRequestPrettyPrintedDTO(httpRequest));
            } else {
                return objectWriter.writeValueAsString(new HttpRequestDTO(httpRequest));
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing HttpRequest to JSON with value " + httpRequest)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequest to JSON with value " + httpRequest, e);
        }
    }

    public String serialize(List<HttpRequest> httpRequests) {
        return serialize(false, httpRequests);
    }

    public String serialize(boolean prettyPrint, List<HttpRequest> httpRequests) {
        return serialize(prettyPrint, httpRequests.toArray(new HttpRequest[0]));
    }

    public String serialize(HttpRequest... httpRequests) {
        return serialize(false, httpRequests);
    }

    public String serialize(boolean prettyPrint, HttpRequest... httpRequests) {
        try {
            if (httpRequests != null && httpRequests.length > 0) {
                if (prettyPrint) {
                    HttpRequestPrettyPrintedDTO[] httpRequestTemplateObjects = new HttpRequestPrettyPrintedDTO[httpRequests.length];
                    for (int i = 0; i < httpRequests.length; i++) {
                        httpRequestTemplateObjects[i] = new HttpRequestPrettyPrintedDTO(httpRequests[i]);
                    }
                    return objectWriter.writeValueAsString(httpRequestTemplateObjects);
                } else {
                    HttpRequestDTO[] httpRequestDTOs = new HttpRequestDTO[httpRequests.length];
                    for (int i = 0; i < httpRequests.length; i++) {
                        httpRequestDTOs[i] = new HttpRequestDTO(httpRequests[i]);
                    }
                    return objectWriter.writeValueAsString(httpRequestDTOs);
                }
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequests))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequests), e);
        }
    }

    public HttpRequest deserialize(String jsonHttpRequest) {
        if (isBlank(jsonHttpRequest)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE
                    + " - a request is required but value was \"" + jsonHttpRequest + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            if (jsonHttpRequest.contains("\"httpRequest\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonHttpRequest);
                    if (jsonNode.has("httpRequest")) {
                        jsonHttpRequest = jsonNode.get("httpRequest").toString();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for HttpRequest " + throwable.getMessage())
                            .setArguments(jsonHttpRequest)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonHttpRequest + "] for HttpRequest", throwable);
                }
            }
            String validationErrors = getValidator().isValid(jsonHttpRequest);
            if (validationErrors.isEmpty()) {
                HttpRequest httpRequest = null;
                try {
                    HttpRequestDTO httpRequestDTO = objectMapper.readValue(jsonHttpRequest, HttpRequestDTO.class);
                    if (httpRequestDTO != null) {
                        httpRequest = httpRequestDTO.buildObject();
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for HttpRequest" + throwable.getMessage())
                            .setArguments(jsonHttpRequest)
                            .setThrowable(throwable)
                    );
                    throw new IllegalArgumentException("exception while parsing [" + jsonHttpRequest + "] for HttpRequest", throwable);
                }
                return httpRequest;
            } else {
                throw new IllegalArgumentException(StringUtils.removeEndIgnoreCase(formatLogMessage("incorrect request json format for:{}schema validation errors:{}", jsonHttpRequest, validationErrors), "\n"));
            }
        }
    }

    @Override
    public Class<HttpRequest> supportsType() {
        return HttpRequest.class;
    }

    public HttpRequest[] deserializeArray(String jsonHttpRequests) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        if (isBlank(jsonHttpRequests)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or request array is required but value was \"" + jsonHttpRequests + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.splitJSONArray(jsonHttpRequests);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or array of request is required");
            } else {
                List<String> validationErrorsList = new ArrayList<>();
                for (String jsonRequest : jsonRequestList) {
                    try {
                        httpRequests.add(deserialize(jsonRequest));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on("," + NEW_LINE).join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return httpRequests.toArray(new HttpRequest[0]);
    }

}
