package org.mockserver.serialization.deserializers.expectation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OpenAPIExpectationDTODeserializer extends StdDeserializer<OpenAPIExpectationDTO> {

    private static final long serialVersionUID = 1L;

    public OpenAPIExpectationDTODeserializer() {
        super(OpenAPIExpectationDTO.class);
    }

    @Override
    public OpenAPIExpectationDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            String specUrlOrPayload = null;
            Map<String, Object> operationsAndResponses = null;
            String contextPathPrefix = null;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.currentName();
                switch (fieldName) {
                    case "specUrlOrPayload":
                        jsonParser.nextToken();
                        JsonNode specUrlOrPayloadField = ctxt.readValue(jsonParser, JsonNode.class);
                        if (specUrlOrPayloadField.isTextual()) {
                            specUrlOrPayload = specUrlOrPayloadField.asText();
                        } else {
                            specUrlOrPayload = specUrlOrPayloadField.toPrettyString();
                        }
                        break;
                    case "operationsAndResponses":
                        jsonParser.nextToken();
                        Map<String, Object> value = new HashMap<>();
                        Map<?, ?> map = ctxt.readValue(jsonParser, Map.class);
                        map.keySet().forEach(key -> {
                            if (key instanceof String && map.get(key) != null) {
                                value.put((String) key, map.get(key));
                            }
                        });
                        if (!value.isEmpty()) {
                            operationsAndResponses = value;
                        }
                        break;
                    case "contextPathPrefix":
                        jsonParser.nextToken();
                        contextPathPrefix = jsonParser.getValueAsString();
                        break;
                }
            }
            return new OpenAPIExpectationDTO()
                .setSpecUrlOrPayload(specUrlOrPayload)
                .setOperationsAndResponses(operationsAndResponses)
                .setContextPathPrefix(contextPathPrefix);
        }
        return null;
    }
}
