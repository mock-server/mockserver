package org.mockserver.templates.engine.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class JsonTemplateHelper {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String merge(String json1, String json2) {
        try {
            JsonNode node1 = objectMapper.readTree(json1);
            JsonNode node2 = objectMapper.readTree(json2);
            if (node1 instanceof ObjectNode && node2 instanceof ObjectNode) {
                ObjectNode merged = ((ObjectNode) node1).deepCopy();
                Iterator<String> fieldNames = node2.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    merged.set(fieldName, node2.get(fieldName).deepCopy());
                }
                return objectMapper.writeValueAsString(merged);
            }
            return json1;
        } catch (JsonProcessingException e) {
            return json1;
        }
    }

    public String sort(String jsonArray, String field) {
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            if (node instanceof ArrayNode) {
                List<JsonNode> elements = new ArrayList<>();
                node.forEach(elements::add);
                elements.sort(Comparator.comparing(n -> {
                    JsonNode fieldNode = n.get(field);
                    return fieldNode != null ? fieldNode.asText() : "";
                }));
                ArrayNode sorted = objectMapper.createArrayNode();
                elements.forEach(sorted::add);
                return objectMapper.writeValueAsString(sorted);
            }
            return jsonArray;
        } catch (JsonProcessingException e) {
            return jsonArray;
        }
    }

    public String arrayAdd(String jsonArray, String element) {
        try {
            JsonNode arrayNode = objectMapper.readTree(jsonArray);
            JsonNode elementNode = objectMapper.readTree(element);
            if (arrayNode instanceof ArrayNode) {
                ((ArrayNode) arrayNode).add(elementNode);
                return objectMapper.writeValueAsString(arrayNode);
            }
            return jsonArray;
        } catch (JsonProcessingException e) {
            return jsonArray;
        }
    }

    public String remove(String json, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode) {
                ((ObjectNode) node).remove(fieldName);
                return objectMapper.writeValueAsString(node);
            }
            return json;
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    public String prettyPrint(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    public String field(String json, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode != null) {
                return fieldNode.isTextual() ? fieldNode.asText() : objectMapper.writeValueAsString(fieldNode);
            }
            return "";
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public int size(String jsonArray) {
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            return node.isArray() ? node.size() : 0;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "JsonTemplateHelper";
    }
}
