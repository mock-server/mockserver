package org.mockserver.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.WebSocketMessageDTO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked", "FieldMayBeFinal"})
public class WebSocketMessageSerializer {

    private static final Map<String, Class> ALLOWED_TYPES = new HashMap<>();
    static {
        ALLOWED_TYPES.put(HttpRequest.class.getName(), HttpRequest.class);
        ALLOWED_TYPES.put(HttpResponse.class.getName(), HttpResponse.class);
        ALLOWED_TYPES.put(HttpRequestAndHttpResponse.class.getName(), HttpRequestAndHttpResponse.class);
    }
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private Map<Class, Serializer> serializers;

    public WebSocketMessageSerializer(MockServerLogger mockServerLogger) {
        serializers = ImmutableMap.of(
            HttpRequest.class, new HttpRequestSerializer(mockServerLogger),
            HttpResponse.class, new HttpResponseSerializer(mockServerLogger),
            HttpRequestAndHttpResponse.class, new HttpRequestAndHttpResponseSerializer(mockServerLogger)
        );
    }

    public String serialize(Object message) throws JsonProcessingException {
        if (serializers.containsKey(message.getClass())) {
            WebSocketMessageDTO value = new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(serializers.get(message.getClass()).serialize((message)));
            return objectWriter.writeValueAsString(value);
        } else {
            return objectWriter.writeValueAsString(new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(objectMapper.writeValueAsString(message)));
        }
    }

    public Object deserialize(String messageJson) throws IOException {
        WebSocketMessageDTO webSocketMessageDTO = objectMapper.readValue(messageJson, WebSocketMessageDTO.class);
        if (webSocketMessageDTO.getType() != null && webSocketMessageDTO.getValue() != null) {
            Class format = ALLOWED_TYPES.get(webSocketMessageDTO.getType());
            if (format == null) {
                throw new IllegalArgumentException("Unsupported WebSocket message type: " + webSocketMessageDTO.getType());
            }
            if (serializers.containsKey(format)) {
                return serializers.get(format).deserialize(webSocketMessageDTO.getValue());
            } else {
                return objectMapper.readValue(webSocketMessageDTO.getValue(), format);
            }
        } else {
            return null;
        }
    }
}
