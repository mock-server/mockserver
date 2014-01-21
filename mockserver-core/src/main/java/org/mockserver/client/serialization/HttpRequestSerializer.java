package org.mockserver.client.serialization;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
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

    public HttpRequestSerializer() {
        // ignore failures
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        // relax parsing
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // use arrays
        objectMapper.configure(DeserializationConfig.Feature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY);
    }

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
                HttpRequestDTO httpRequestDTO = objectMapper.readValue(jsonHttpRequest, HttpRequestDTO.class);
                if (httpRequestDTO != null) {
                    httpRequest = httpRequestDTO.buildObject();
                }
            } catch (IOException ioe) {
                logger.info("Exception while parsing response [" + new String(jsonHttpRequest) + "] for http response httpRequest", ioe);
            }
        }
        return httpRequest;
    }

}
