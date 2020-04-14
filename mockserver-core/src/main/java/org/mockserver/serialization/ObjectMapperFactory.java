package org.mockserver.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.mockserver.serialization.deserializers.body.BodyDTODeserializer;
import org.mockserver.serialization.deserializers.body.BodyWithContentTypeDTODeserializer;
import org.mockserver.serialization.deserializers.collections.CookiesDeserializer;
import org.mockserver.serialization.deserializers.collections.HeadersDeserializer;
import org.mockserver.serialization.deserializers.collections.ParametersDeserializer;
import org.mockserver.serialization.deserializers.collections.SessionDeserializer;
import org.mockserver.serialization.deserializers.condition.TimeToLiveDTODeserializer;
import org.mockserver.serialization.deserializers.condition.VerificationTimesDTODeserializer;
import org.mockserver.serialization.deserializers.string.NottableStringDeserializer;
import org.mockserver.serialization.serializers.body.*;
import org.mockserver.serialization.serializers.collections.CookiesSerializer;
import org.mockserver.serialization.serializers.collections.HeadersSerializer;
import org.mockserver.serialization.serializers.collections.ParametersSerializer;
import org.mockserver.serialization.serializers.collections.SessionSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesDTOSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesSerializer;
import org.mockserver.serialization.serializers.request.HttpRequestDTOSerializer;
import org.mockserver.serialization.serializers.response.HttpResponseSerializer;
import org.mockserver.serialization.serializers.response.*;
import org.mockserver.serialization.serializers.string.NottableStringSerializer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockserver.exception.ExceptionHandling.swallowThrowable;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectMapperFactory {

    private static ObjectMapper objectMapper = buildObjectMapper();

    public static ObjectMapper createObjectMapper(JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            if (objectMapper == null) {
                objectMapper = buildObjectMapper();
            }
            return objectMapper;
        } else {
            return buildObjectMapper(additionJsonSerializers);
        }
    }

    @SuppressWarnings("deprecation")
    private static ObjectMapper buildObjectMapper(JsonSerializer... additionJsonSerializers) {
        ObjectMapper objectMapper = new ObjectMapper();

        // ignore failures
        swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false));
        swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false));
        swallowThrowable(() -> objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false));
        swallowThrowable(() -> objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false));

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

        // register our own module with our serializers and deserializers
        SimpleModule module = new SimpleModule();
        addDeserializers(module);
        addSerializers(module, additionJsonSerializers);
        objectMapper.registerModule(module);
        return objectMapper;
    }

    private static void addDeserializers(SimpleModule module) {
        List<JsonDeserializer> jsonDeserializers = Arrays.asList(
            // times
            new TimeToLiveDTODeserializer(),
            // request body
            new BodyDTODeserializer(),
            new BodyWithContentTypeDTODeserializer(),
            // condition
            new VerificationTimesDTODeserializer(),
            // nottable string
            new NottableStringDeserializer(),
            // key and multivalue
            new HeadersDeserializer(),
            new ParametersDeserializer(),
            new CookiesDeserializer(),
            new SessionDeserializer()
        );
        for (JsonDeserializer jsonDeserializer : jsonDeserializers) {
            Class type = jsonDeserializer.handledType();
            module.addDeserializer(type, jsonDeserializer);
        }
    }

    private static void addSerializers(SimpleModule module, JsonSerializer[] additionJsonSerializers) {
        List<JsonSerializer> jsonSerializers = Arrays.asList(
            // times
            new TimesSerializer(),
            new TimesDTOSerializer(),
            new TimeToLiveDTOSerializer(),
            // request
            new org.mockserver.serialization.serializers.request.HttpRequestSerializer(),
            new HttpRequestDTOSerializer(),
            // request body
            new BinaryBodySerializer(),
            new BinaryBodyDTOSerializer(),
            new JsonBodySerializer(),
            new JsonBodyDTOSerializer(),
            new JsonSchemaBodySerializer(),
            new JsonSchemaBodyDTOSerializer(),
            new JsonPathBodySerializer(),
            new JsonPathBodyDTOSerializer(),
            new ParameterBodySerializer(),
            new ParameterBodyDTOSerializer(),
            new RegexBodySerializer(),
            new RegexBodyDTOSerializer(),
            new StringBodySerializer(),
            new StringBodyDTOSerializer(),
            new XmlBodySerializer(),
            new XmlBodyDTOSerializer(),
            new XmlSchemaBodySerializer(),
            new XmlSchemaBodyDTOSerializer(),
            new XPathBodySerializer(),
            new XPathBodyDTOSerializer(),
            new LogEventBodySerializer(),
            new LogEventBodyDTOSerializer(),
            // condition
            new VerificationTimesDTOSerializer(),
            new VerificationTimesSerializer(),
            // nottable string
            new NottableStringSerializer(),
            // response
            new HttpResponseSerializer(),
            new HttpResponseDTOSerializer(),
            // key and multivalue
            new HeadersSerializer(),
            new ParametersSerializer(),
            new CookiesSerializer(),
            new SessionSerializer(),
            // log
            new org.mockserver.serialization.serializers.log.LogEntrySerializer()
        );
        Map<Class, JsonSerializer> jsonSerializersByType = new HashMap<>();
        for (JsonSerializer jsonSerializer : jsonSerializers) {
            jsonSerializersByType.put(jsonSerializer.handledType(), jsonSerializer);
        }
        // override any existing serializers
        for (JsonSerializer additionJsonSerializer : additionJsonSerializers) {
            jsonSerializersByType.put(additionJsonSerializer.handledType(), additionJsonSerializer);
        }
        for (Map.Entry<Class, JsonSerializer> additionJsonSerializer : jsonSerializersByType.entrySet()) {
            module.addSerializer(additionJsonSerializer.getKey(), additionJsonSerializer.getValue());
        }
    }

}
