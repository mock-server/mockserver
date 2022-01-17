package org.mockserver.examples.proxy.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.mockserver.exception.ExceptionHandling.swallowThrowable;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactory {
    private static ObjectMapper objectMapper;

    @SuppressWarnings("deprecation")
    public static ObjectMapper createObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();

            // ignore failures
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false));
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false));
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false));
            swallowThrowable(() -> objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES, true));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, true));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, false));
            swallowThrowable(() -> objectMapper.configure(MapperFeature.AUTO_DETECT_GETTERS, true));

            // relax parsing
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true));
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true));
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true));
            swallowThrowable(() -> objectMapper.configure(JsonParser.Feature.IGNORE_UNDEFINED, true));

            // use arrays
            swallowThrowable(() -> objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true));

            // remove empty values from JSON
            swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT));
            swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL));
            swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY));

            // consistent json output
            swallowThrowable(() -> objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true));
        }
        return objectMapper;
    }
}
