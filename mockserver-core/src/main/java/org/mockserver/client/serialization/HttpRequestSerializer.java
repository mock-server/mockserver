package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializer implements Serializer<HttpRequest> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(HttpRequest httpRequest) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HttpRequestDTO(httpRequest));
        } catch (Exception e) {
            logger.error(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), e);
            throw new RuntimeException(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), e);
        }
    }

    public HttpRequest deserialize(String jsonHttpRequest) {
        HttpRequest httpRequest = null;
        if (jsonHttpRequest != null && !jsonHttpRequest.isEmpty()) {
            try {
                if (!jsonHttpRequest.contains("\"httpRequest\"")) {
                    HttpRequestDTO httpRequestDTO = objectMapper.readValue(jsonHttpRequest, HttpRequestDTO.class);
                    if (httpRequestDTO != null) {
                        httpRequest = httpRequestDTO.buildObject();
                    }
                } else {
                    ExpectationDTO expectationDTO = objectMapper.readValue(jsonHttpRequest, ExpectationDTO.class);
                    if (expectationDTO != null) {
                        httpRequest = expectationDTO.buildObject().getHttpRequest();
                    }
                }
            } catch (Exception e) {
                logger.info("Exception while parsing HttpRequest for [" + jsonHttpRequest + "]", e);
                throw new RuntimeException("Exception while parsing HttpRequest for [" + jsonHttpRequest + "]", e);
            }
        }
        return httpRequest;
    }

    @Override
    public Class<HttpRequest> supportsType() {
        return HttpRequest.class;
    }

    public HttpRequest[] deserializeArray(String jsonHttpRequests) {
        HttpRequest[] httpRequests = new HttpRequest[]{};
        if (jsonHttpRequests != null && !jsonHttpRequests.isEmpty()) {
            try {
                HttpRequestDTO[] httpRequestDTOs = objectMapper.readValue(jsonHttpRequests, HttpRequestDTO[].class);
                if (httpRequestDTOs != null && httpRequestDTOs.length > 0) {
                    httpRequests = new HttpRequest[httpRequestDTOs.length];
                    for (int i = 0; i < httpRequestDTOs.length; i++) {
                        httpRequests[i] = httpRequestDTOs[i].buildObject();
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while parsing response [" + jsonHttpRequests + "] for HttpRequest[]", e);
                throw new RuntimeException("Exception while parsing response [" + jsonHttpRequests + "] for HttpRequest[]", e);
            }
        }
        return httpRequests;
    }

    public String serialize(List<HttpRequest> httpRequests) {
        return serialize(httpRequests.toArray(new HttpRequest[httpRequests.size()]));
    }

    public String serialize(HttpRequest... httpRequest) {
        try {
            if (httpRequest != null && httpRequest.length > 0) {
                HttpRequestDTO[] httpRequestDTOs = new HttpRequestDTO[httpRequest.length];
                for (int i = 0; i < httpRequest.length; i++) {
                    httpRequestDTOs[i] = new HttpRequestDTO(httpRequest[i]);
                }
                return objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(httpRequestDTOs);
            }
            return "";
        } catch (Exception e) {
            logger.error("Exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequest), e);
            throw new RuntimeException("Exception while serializing HttpRequest to JSON with value " + Arrays.asList(httpRequest), e);
        }
    }
}
