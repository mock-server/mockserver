package org.mockserver.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.WebSocketMessageDTO;

import java.io.IOException;
import java.util.Map;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class WebSocketMessageSerializer {

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
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } else {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(objectMapper.writeValueAsString(message)));
        }
    }

    public Object deserialize(String messageJson) throws ClassNotFoundException, IOException {
        WebSocketMessageDTO webSocketMessageDTO = objectMapper.readValue(messageJson, WebSocketMessageDTO.class);
        if (webSocketMessageDTO.getType() != null && webSocketMessageDTO.getValue() != null) {
            Class format = Class.forName(webSocketMessageDTO.getType());
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
