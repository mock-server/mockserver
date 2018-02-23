package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializer implements Serializer<HttpRequest> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaHttpRequestValidator httpRequestValidator;

    public HttpRequestSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        httpRequestValidator = new JsonSchemaHttpRequestValidator(mockServerLogger);
    }

    public String serialize(HttpRequest httpRequest) {
        return serialize(false, httpRequest);
    }

    public String serialize(boolean prettyPrint, HttpRequest httpRequest) {
        try {
            if (prettyPrint) {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HttpRequestTemplateObject(httpRequest));
            } else {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HttpRequestDTO(httpRequest));
            }
        } catch (Exception e) {
            mockServerLogger.error(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), e);
            throw new RuntimeException(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), e);
        }
    }

    public String serialize(List<HttpRequest> httpRequests) {
        return serialize(false, httpRequests);
    }

    public String serialize(boolean prettyPrint, List<HttpRequest> httpRequests) {
        return serialize(prettyPrint, httpRequests.toArray(new HttpRequest[httpRequests.size()]));
    }

    public String serialize(HttpRequest... httpRequests) {
        return serialize(false, httpRequests);
    }

    public String serialize(boolean prettyPrint, HttpRequest... httpRequests) {
        try {
            if (httpRequests != null && httpRequests.length > 0) {
                if (prettyPrint) {
                    HttpRequestTemplateObject[] httpRequestTemplateObjects = new HttpRequestTemplateObject[httpRequests.length];
                    for (int i = 0; i < httpRequests.length; i++) {
                        httpRequestTemplateObjects[i] = new HttpRequestTemplateObject(httpRequests[i]);
                    }
                    return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpRequestTemplateObjects);
                } else {
                    HttpRequestDTO[] httpRequestDTOs = new HttpRequestDTO[httpRequests.length];
                    for (int i = 0; i < httpRequests.length; i++) {
                        httpRequestDTOs[i] = new HttpRequestDTO(httpRequests[i]);
                    }
                    return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpRequestDTOs);
                }
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequests), e);
            throw new RuntimeException("Exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequests), e);
        }
    }

    public HttpRequest deserialize(String jsonHttpRequest) {
        if (Strings.isNullOrEmpty(jsonHttpRequest)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request is required but value was \"" + String.valueOf(jsonHttpRequest) + "\"");
        } else {
            if (jsonHttpRequest.contains("\"httpRequest\"")) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonHttpRequest);
                    if (jsonNode.has("httpRequest")) {
                        jsonHttpRequest = jsonNode.get("httpRequest").toString();
                    }
                } catch (Exception e) {
                    mockServerLogger.error("Exception while parsing [" + jsonHttpRequest + "] for HttpRequest", e);
                    throw new RuntimeException("Exception while parsing [" + jsonHttpRequest + "] for HttpRequest", e);
                }
            }
            String validationErrors = httpRequestValidator.isValid(jsonHttpRequest);
            if (validationErrors.isEmpty()) {
                HttpRequest httpRequest = null;
                try {
                    HttpRequestDTO httpRequestDTO = objectMapper.readValue(jsonHttpRequest, HttpRequestDTO.class);
                    if (httpRequestDTO != null) {
                        httpRequest = httpRequestDTO.buildObject();
                    }
                } catch (Exception e) {
                    mockServerLogger.error((HttpRequest) null, e, "exception while parsing {} for HttpRequest", jsonHttpRequest);
                    throw new RuntimeException("Exception while parsing [" + jsonHttpRequest + "] for HttpRequest", e);
                }
                return httpRequest;
            } else {
                mockServerLogger.error("validation failed:{}HttpRequest:{}", validationErrors, jsonHttpRequest);
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<HttpRequest> supportsType() {
        return HttpRequest.class;
    }

    public HttpRequest[] deserializeArray(String jsonHttpRequests) {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        if (Strings.isNullOrEmpty(jsonHttpRequests)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or request array is required but value was \"" + String.valueOf(jsonHttpRequests) + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.returnJSONObjects(jsonHttpRequests);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or array of request is required");
            } else {
                List<String> validationErrorsList = new ArrayList<String>();
                for (String jsonExpecation : jsonRequestList) {
                    try {
                        httpRequests.add(deserialize(jsonExpecation));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on("," + NEW_LINE).join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return httpRequests.toArray(new HttpRequest[httpRequests.size()]);
    }

}
