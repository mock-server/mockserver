package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import joptsimple.internal.Strings;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpResponse;
import org.mockserver.validator.JsonSchemaHttpResponseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializer implements Serializer<HttpResponse> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private JsonSchemaHttpResponseValidator httpResponseValidator = new JsonSchemaHttpResponseValidator();

    public String serialize(HttpResponse httpResponse) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HttpResponseDTO(httpResponse));
        } catch (Exception e) {
            logger.error(String.format("Exception while serializing httpResponse to JSON with value %s", httpResponse), e);
            throw new RuntimeException(String.format("Exception while serializing httpResponse to JSON with value %s", httpResponse), e);
        }
    }

    public String serialize(List<HttpResponse> httpResponses) {
        return serialize(httpResponses.toArray(new HttpResponse[httpResponses.size()]));
    }

    public String serialize(HttpResponse... httpResponses) {
        try {
            if (httpResponses != null && httpResponses.length > 0) {
                HttpResponseDTO[] httpResponseDTOs = new HttpResponseDTO[httpResponses.length];
                for (int i = 0; i < httpResponses.length; i++) {
                    httpResponseDTOs[i] = new HttpResponseDTO(httpResponses[i]);
                }
                return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpResponseDTOs);
            }
            return "";
        } catch (Exception e) {
            logger.error("Exception while serializing HttpResponse to JSON with value " + Arrays.asList(httpResponses), e);
            throw new RuntimeException("Exception while serializing HttpResponse to JSON with value " + Arrays.asList(httpResponses), e);
        }
    }

    public HttpResponse deserialize(String jsonHttpResponse) {
        if (Strings.isNullOrEmpty(jsonHttpResponse)) {
            throw new IllegalArgumentException("1 error:\n - a response is required but value was \"" + String.valueOf(jsonHttpResponse) + "\"");
        } else {
            String validationErrors = httpResponseValidator.isValid(jsonHttpResponse);
            if (validationErrors.isEmpty()) {
                HttpResponse httpResponse = null;
                try {
                    HttpResponseDTO httpResponseDTO = objectMapper.readValue(jsonHttpResponse, HttpResponseDTO.class);
                    if (httpResponseDTO != null) {
                        httpResponse = httpResponseDTO.buildObject();
                    }
                } catch (Exception e) {
                    logger.error("Exception while parsing [" + jsonHttpResponse + "] for HttpResponse", e);
                    throw new RuntimeException("Exception while parsing [" + jsonHttpResponse + "] for HttpResponse", e);
                }
                return httpResponse;
            } else {
                logger.info("Validation failed:" + NEW_LINE + jsonHttpResponse + NEW_LINE + "-- HttpResponse:" + NEW_LINE + jsonHttpResponse + NEW_LINE + "-- Schema:" + NEW_LINE + httpResponseValidator.getSchema());
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<HttpResponse> supportsType() {
        return HttpResponse.class;
    }

    public HttpResponse[] deserializeArray(String jsonHttpResponses) {
        List<HttpResponse> httpResponses = new ArrayList<HttpResponse>();
        if (Strings.isNullOrEmpty(jsonHttpResponses)) {
            throw new IllegalArgumentException("1 error:\n - a response or response array is required but value was \"" + String.valueOf(jsonHttpResponses) + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.returnJSONObjects(jsonHttpResponses);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:\n - a response or array of response is required");
            } else {
                List<String> validationErrorsList = new ArrayList<String>();
                for (String jsonExpecation : jsonRequestList) {
                    try {
                        httpResponses.add(deserialize(jsonExpecation));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on(",\n").join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return httpResponses.toArray(new HttpResponse[httpResponses.size()]);
    }

}
