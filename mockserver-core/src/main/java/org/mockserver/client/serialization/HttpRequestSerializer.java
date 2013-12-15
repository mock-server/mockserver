package org.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
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
    private ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(HttpRequest httpRequest) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new HttpRequestDTO(httpRequest));
        } catch (IOException ioe) {
            logger.error(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), ioe);
            throw new RuntimeException(String.format("Exception while serializing httpRequest to JSON with value %s", httpRequest), ioe);
        }
    }

    public HttpRequest deserialize(byte[] jsonHttpRequest) {
        if (jsonHttpRequest.length == 0) throw new IllegalArgumentException("Expected an JSON httpRequest object but http body is empty");
        HttpRequest httpRequest = null;
        try {
            HttpRequestDTO httpRequestDTO = objectMapper.readValue(jsonHttpRequest, HttpRequestDTO.class);
            if (httpRequestDTO != null) {
                httpRequest = httpRequestDTO.buildObject();
            }
        } catch (IOException ioe) {
            logger.error("Exception while parsing response [" + new String(jsonHttpRequest) + "] for http response httpRequest", ioe);
            throw new RuntimeException("Exception while parsing response [" + new String(jsonHttpRequest) + "] for http response httpRequest", ioe);
        }
        return httpRequest;
    }

}
