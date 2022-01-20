package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JacksonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author jamesdbloom
 */
public class JsonArraySerializer {
    private static final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public List<String> splitJSONArray(String jsonArray) {
        return splitJSONArrayToJSONNodes(jsonArray).stream().map(JacksonUtils::prettyPrint).collect(Collectors.toList());
    }

    public List<JsonNode> splitJSONArrayToJSONNodes(String jsonArray) {
        List<JsonNode> arrayItems = new ArrayList<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonArray);
            if (jsonNode instanceof ArrayNode) {
                for (JsonNode arrayElement : jsonNode) {
                    arrayItems.add(arrayElement);
                }
            } else {
                arrayItems.add(jsonNode);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return arrayItems;
    }

}
