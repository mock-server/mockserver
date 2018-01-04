package org.mockserver.client.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.mockserver.client.serialization.model.WebSocketMessageDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class WebSocketMessageSerializer {

    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private Map<Class, Serializer> serializers = ImmutableMap.<Class, Serializer>of(
        HttpRequest.class, new HttpRequestSerializer(),
        HttpResponse.class, new HttpResponseSerializer()
    );

    public String serialize(Object message) throws JsonProcessingException {
        if (serializers.containsKey(message.getClass())) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new WebSocketMessageDTO().setType(message.getClass().getName()).setValue(serializers.get(message.getClass()).serialize((message))));
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
