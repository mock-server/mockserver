package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place expectation metadata is converted to and from JSON, so the expectation, its DTO and
 * {@link ExpectationFilter} always agree on how a value is written. Loaded only when metadata is used.
 */
public final class MetadataJson {

    // MockServer's mapper (Java time and the other registered modules), but keeping null values: a metadata key
    // whose value is null is still a key
    public static final ObjectMapper WRITER = ObjectMapperFactory.createObjectMapper()
        .copy()
        .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS));
    // a plain mapper reads arrays as List, which unlike MockServer's Object[] has value equality
    private static final ObjectMapper READER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<LinkedHashMap<String, Object>>() {
    };

    private MetadataJson() {
    }

    /**
     * @throws IllegalArgumentException if the value cannot be written as JSON
     */
    public static String toJson(Object value) {
        try {
            return WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("expectation metadata is not serialisable as JSON: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static Object fromJson(String json) {
        try {
            return READER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("expectation metadata is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Converts deserialised metadata to plain JSON types (List, Map, String, Number, Boolean).
     */
    public static Map<String, Object> toPlainJson(Map<String, Object> metadata) {
        return metadata != null ? READER.convertValue(metadata, MAP) : null;
    }
}
