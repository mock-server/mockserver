package org.mockserver.examples.proxy.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.mockserver.exception.ExceptionHandling;

import static org.mockserver.exception.ExceptionHandling.handleThrowable;

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
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, false));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.AUTO_DETECT_GETTERS, true));

            // relax parsing
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true));
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(JsonParser.Feature.IGNORE_UNDEFINED, true));

            // use arrays
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true));

            // remove empty values from JSON
            ExceptionHandling.handleThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT));
            ExceptionHandling.handleThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL));
            ExceptionHandling.handleThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY));

            // consistent json output
            ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true));
        }
        return objectMapper;
    }
}
