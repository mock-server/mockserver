package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JacksonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class JsonArraySerializer {
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public List<String> returnJSONObjects(String jsonArray) {
        List<String> arrayItems = new ArrayList<String>();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonArray);
            if (jsonNode instanceof ArrayNode) {
                for (JsonNode arrayElement : jsonNode) {
                    arrayItems.add(JacksonUtils.prettyPrint(arrayElement));
                }
            } else {
                arrayItems.add(JacksonUtils.prettyPrint(jsonNode));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return arrayItems;
    }

}
