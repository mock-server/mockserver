package org.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(HttpRequest httpRequest) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HttpRequestDTO(httpRequest));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), ioe);
            throw new RuntimeException(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), ioe);
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
            } catch (IOException ioe) {
                logger.info("Exception while parsing response [" + jsonHttpRequest + "] for http response httpRequest", ioe);
            }
        }
        return httpRequest;
    }

}
