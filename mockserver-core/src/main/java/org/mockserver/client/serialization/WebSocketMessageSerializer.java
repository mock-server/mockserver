package org.mockserver.client.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.client.serialization.model.WebSocketMessageDTO;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class WebSocketMessageSerializer {

    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private static Map<Class, Serializer> serializers = new HashMap<Class, Serializer>();

    static {
        for (Serializer serializer : Arrays.asList(
                new HttpRequestSerializer(),
                new HttpResponseSerializer()
        )) {
            serializers.put(serializer.supportsType(), serializer);
        }
    }

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
