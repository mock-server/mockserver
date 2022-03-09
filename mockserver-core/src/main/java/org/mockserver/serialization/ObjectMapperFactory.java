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
import org.mockserver.serialization.deserializers.condition.TimeToLiveDTODeserializer;
import org.mockserver.serialization.deserializers.condition.VerificationTimesDTODeserializer;
import org.mockserver.serialization.deserializers.expectation.OpenAPIExpectationDTODeserializer;
import org.mockserver.serialization.deserializers.request.RequestDefinitionDTODeserializer;
import org.mockserver.serialization.deserializers.string.NottableStringDeserializer;
import org.mockserver.serialization.serializers.body.*;
import org.mockserver.serialization.serializers.collections.CookiesSerializer;
import org.mockserver.serialization.serializers.collections.HeadersSerializer;
import org.mockserver.serialization.serializers.collections.ParametersSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesDTOSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationDTOSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationSerializer;
import org.mockserver.serialization.serializers.matcher.HttpRequestPropertiesMatcherSerializer;
import org.mockserver.serialization.serializers.matcher.HttpRequestsPropertiesMatcherSerializer;
import org.mockserver.serialization.serializers.request.HttpRequestDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionSerializer;
import org.mockserver.serialization.serializers.certificate.X509CertificateSerializer;
import org.mockserver.serialization.serializers.response.HttpResponseSerializer;
import org.mockserver.serialization.serializers.response.*;
import org.mockserver.serialization.serializers.schema.*;
import org.mockserver.serialization.serializers.string.NottableStringSerializer;

import java.util.*;

import static org.mockserver.exception.ExceptionHandling.swallowThrowable;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectMapperFactory {

    private static ObjectMapper objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList());
    private static final ObjectWriter prettyPrintWriter = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList()).writerWithDefaultPrettyPrinter();
    private static final ObjectWriter writer = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList()).writer();

    public static ObjectMapper createObjectMapper() {
        if (objectMapper == null) {
            objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList());
        }
        return objectMapper;
    }

    public static ObjectMapper createObjectMapper(JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            if (objectMapper == null) {
                objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList());
            }
            return objectMapper;
        } else {
            return buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Arrays.asList(additionJsonSerializers));
        }
    }

    public static ObjectMapper createObjectMapper(JsonDeserializer... replacementJsonDeserializers) {
        if (replacementJsonDeserializers == null || replacementJsonDeserializers.length == 0) {
            if (objectMapper == null) {
                objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList());
            }
            return objectMapper;
        } else {
            return buildObjectMapperWithDeserializerAndSerializers(Arrays.asList(replacementJsonDeserializers), Collections.emptyList());
        }
    }

    public static ObjectWriter createObjectMapper(boolean pretty, JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            if (pretty) {
                return prettyPrintWriter;
            } else {
                return writer;
            }
        } else {
            if (pretty) {
                return buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Arrays.asList(additionJsonSerializers)).writerWithDefaultPrettyPrinter();
            } else {
                return buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Arrays.asList(additionJsonSerializers)).writer();
            }
        }
    }

    public static ObjectMapper buildObjectMapperWithoutRemovingEmptyValues() {
        ObjectMapper objectMapper = new ObjectMapper();

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

        // consistent json output
        swallowThrowable(() -> objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true));

        return objectMapper;
    }

    public static ObjectMapper buildObjectMapperWithOnlyConfigurationDefaults() {
        ObjectMapper objectMapper = buildObjectMapperWithoutRemovingEmptyValues();

        // remove empty values from JSON
        swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT));
        swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL));
        swallowThrowable(() -> objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY));

        return objectMapper;
    }

    private static ObjectMapper buildObjectMapperWithDeserializerAndSerializers(List<JsonDeserializer> replacementJsonDeserializers, List<JsonSerializer> replacementJsonSerializers) {
        ObjectMapper objectMapper = buildObjectMapperWithOnlyConfigurationDefaults();

        // register our own module with our serializers and deserializers
        SimpleModule module = new SimpleModule();
        addDeserializers(module, replacementJsonDeserializers.toArray(new JsonDeserializer[0]));
        addSerializers(module, replacementJsonSerializers.toArray(new JsonSerializer[0]));
        objectMapper.registerModule(module);
        return objectMapper;
    }

    private static void addDeserializers(SimpleModule module, JsonDeserializer[] replacementJsonDeserializers) {
        List<JsonDeserializer> jsonDeserializers = Arrays.asList(
            // expectation
            new OpenAPIExpectationDTODeserializer(),
            // request
            new RequestDefinitionDTODeserializer(),
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
            new CookiesDeserializer()
        );
        Map<Class, JsonDeserializer> jsonDeserializersByType = new HashMap<>();
        for (JsonDeserializer jsonDeserializer : jsonDeserializers) {
            jsonDeserializersByType.put(jsonDeserializer.handledType(), jsonDeserializer);
        }
        // override any existing deserializers
        for (JsonDeserializer additionJsonDeserializer : replacementJsonDeserializers) {
            jsonDeserializersByType.put(additionJsonDeserializer.handledType(), additionJsonDeserializer);
        }
        for (Map.Entry<Class, JsonDeserializer> additionJsonDeserializer : jsonDeserializersByType.entrySet()) {
            module.addDeserializer(additionJsonDeserializer.getKey(), additionJsonDeserializer.getValue());
        }
    }

    private static void addSerializers(SimpleModule module, JsonSerializer[] replacementJsonSerializers) {
        List<JsonSerializer> jsonSerializers = Arrays.asList(
            // expectation
            new OpenAPIExpectationSerializer(),
            new OpenAPIExpectationDTOSerializer(),
            // times
            new TimesSerializer(),
            new TimesDTOSerializer(),
            new TimeToLiveSerializer(),
            new TimeToLiveDTOSerializer(),
            // request
            new org.mockserver.serialization.serializers.request.HttpRequestSerializer(),
            new HttpRequestDTOSerializer(),
            new OpenAPIDefinitionSerializer(),
            new OpenAPIDefinitionDTOSerializer(),
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
            new LogEntryBodySerializer(),
            new LogEntryBodyDTOSerializer(),
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
            // certifcates
            new X509CertificateSerializer(),
            // log
            new org.mockserver.serialization.serializers.log.LogEntrySerializer(),
            // matcher
            new HttpRequestsPropertiesMatcherSerializer(),
            new HttpRequestPropertiesMatcherSerializer(),
            // schema
            new SchemaSerializer(),
            new ArraySchemaSerializer(),
            new BinarySchemaSerializer(),
            new BooleanSchemaSerializer(),
            new ByteArraySchemaSerializer(),
            new ComposedSchemaSerializer(),
            new DateSchemaSerializer(),
            new DateTimeSchemaSerializer(),
            new EmailSchemaSerializer(),
            new FileSchemaSerializer(),
            new IntegerSchemaSerializer(),
            new MapSchemaSerializer(),
            new NumberSchemaSerializer(),
            new ObjectSchemaSerializer(),
            new PasswordSchemaSerializer(),
            new StringSchemaSerializer(),
            new UUIDSchemaSerializer()
        );
        Map<Class, JsonSerializer> jsonSerializersByType = new HashMap<>();
        for (JsonSerializer jsonSerializer : jsonSerializers) {
            jsonSerializersByType.put(jsonSerializer.handledType(), jsonSerializer);
        }
        // override any existing serializers
        for (JsonSerializer additionJsonSerializer : replacementJsonSerializers) {
            jsonSerializersByType.put(additionJsonSerializer.handledType(), additionJsonSerializer);
        }
        for (Map.Entry<Class, JsonSerializer> additionJsonSerializer : jsonSerializersByType.entrySet()) {
            module.addSerializer(additionJsonSerializer.getKey(), additionJsonSerializer.getValue());
        }
    }

}
