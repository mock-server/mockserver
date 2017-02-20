package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializer implements Serializer<HttpResponse> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(HttpResponse httpResponse) {
        try {
            HttpResponseDTO value = new HttpResponseDTO(httpResponse);
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        } catch (Exception e) {
            logger.error(String.format("Exception while serializing httpResponse to JSON with value %s", httpResponse), e);
            throw new RuntimeException(String.format("Exception while serializing httpResponse to JSON with value %s", httpResponse), e);
        }
    }

    public HttpResponse deserialize(String jsonHttpResponse) {
        HttpResponse httpResponse = null;
        if (jsonHttpResponse != null && !jsonHttpResponse.isEmpty()) {
            try {
                if (!jsonHttpResponse.contains("\"httpResponse\"")) {
                    HttpResponseDTO httpResponseDTO = objectMapper.readValue(jsonHttpResponse, HttpResponseDTO.class);
                    if (httpResponseDTO != null) {
                        httpResponse = httpResponseDTO.buildObject();
                    }
                } else {
                    ExpectationDTO expectationDTO = objectMapper.readValue(jsonHttpResponse, ExpectationDTO.class);
                    if (expectationDTO != null) {
                        httpResponse = expectationDTO.buildObject().getHttpResponse();
                    }
                }
            } catch (Exception e) {
                logger.info("Exception while parsing HttpResponse for [" + jsonHttpResponse + "]", e);
                throw new RuntimeException("Exception while parsing HttpResponse for [" + jsonHttpResponse + "]", e);
            }
        }
        return httpResponse;
    }

    @Override
    public Class<HttpResponse> supportsType() {
        return HttpResponse.class;
    }

    public HttpResponse[] deserializeArray(String jsonHttpResponses) {
        HttpResponse[] httpResponses = new HttpResponse[]{};
        if (jsonHttpResponses != null && !jsonHttpResponses.isEmpty()) {
            try {
                HttpResponseDTO[] httpResponseDTOs = objectMapper.readValue(jsonHttpResponses, HttpResponseDTO[].class);
                if (httpResponseDTOs != null && httpResponseDTOs.length > 0) {
                    httpResponses = new HttpResponse[httpResponseDTOs.length];
                    for (int i = 0; i < httpResponseDTOs.length; i++) {
                        httpResponses[i] = httpResponseDTOs[i].buildObject();
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while parsing response [" + jsonHttpResponses + "] for HttpResponse[]", e);
                throw new RuntimeException("Exception while parsing response [" + jsonHttpResponses + "] for HttpResponse[]", e);
            }
        }
        return httpResponses;
    }

    public String serialize(List<HttpResponse> httpResponses) {
        return serialize(httpResponses.toArray(new HttpResponse[httpResponses.size()]));
    }

    public String serialize(HttpResponse[] httpResponse) {
        try {
            if (httpResponse != null && httpResponse.length > 0) {
                HttpResponseDTO[] httpResponseDTOs = new HttpResponseDTO[httpResponse.length];
                for (int i = 0; i < httpResponse.length; i++) {
                    httpResponseDTOs[i] = new HttpResponseDTO(httpResponse[i]);
                }
                return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpResponseDTOs);
            }
            return "";
        } catch (Exception e) {
            logger.error("Exception while serializing HttpResponse to JSON with value " + Arrays.asList(httpResponse), e);
            throw new RuntimeException("Exception while serializing HttpResponse to JSON with value " + Arrays.asList(httpResponse), e);
        }
    }
}
