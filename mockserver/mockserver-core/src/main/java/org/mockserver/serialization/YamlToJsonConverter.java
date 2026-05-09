package org.mockserver.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlToJsonConverter {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public static String convertYamlToJson(String yaml) {
        if (yaml == null || yaml.isEmpty()) {
            return yaml;
        }
        try {
            JsonNode node = YAML_MAPPER.readTree(yaml);
            return JSON_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to convert YAML to JSON: " + e.getMessage(), e);
        }
    }
}
