package org.mockserver.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockserver.exception.ExceptionHandling;
import org.mockserver.serialization.deserializers.body.BodyDTODeserializer;
import org.mockserver.serialization.deserializers.body.BodyWithContentTypeDTODeserializer;
import org.mockserver.serialization.deserializers.collections.CookiesDeserializer;
import org.mockserver.serialization.deserializers.collections.HeadersDeserializer;
import org.mockserver.serialization.deserializers.collections.ParametersDeserializer;
import org.mockserver.serialization.deserializers.condition.TimeToLiveDTODeserializer;
import org.mockserver.serialization.deserializers.condition.VerificationTimesDTODeserializer;
import org.mockserver.serialization.deserializers.certificate.ClientCertificateDeserializer;
import org.mockserver.serialization.deserializers.jwt.JwtDeserializer;
import org.mockserver.serialization.deserializers.expectation.OpenAPIExpectationDTODeserializer;
import org.mockserver.serialization.deserializers.request.RequestDefinitionDTODeserializer;
import org.mockserver.serialization.deserializers.string.NottableStringDeserializer;
import org.mockserver.serialization.serializers.body.*;
import org.mockserver.serialization.serializers.certificate.CertificateSerializer;
import org.mockserver.serialization.serializers.certificate.ClientCertificateSerializer;
import org.mockserver.serialization.serializers.jwt.JwtSerializer;
import org.mockserver.serialization.serializers.collections.CookiesSerializer;
import org.mockserver.serialization.serializers.collections.HeadersSerializer;
import org.mockserver.serialization.serializers.collections.ParametersSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesDTOSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationDTOSerializer;
import org.mockserver.serialization.serializers.expectation.OpenAPIExpectationSerializer;
import org.mockserver.serialization.serializers.matcher.HttpRequestPropertiesMatcherSerializer;
import org.mockserver.serialization.serializers.request.HttpRequestDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionDTOSerializer;
import org.mockserver.serialization.serializers.request.OpenAPIDefinitionSerializer;
import org.mockserver.serialization.serializers.certificate.X509CertificateSerializer;
import org.mockserver.serialization.serializers.response.HttpResponseSerializer;
import org.mockserver.serialization.serializers.response.*;
import org.mockserver.serialization.serializers.string.NottableStringSerializer;

import java.util.*;


/**
 * @author jamesdbloom
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectMapperFactory {

    /**
     * Whether swagger-core (the OpenAPI model classes) is on the classpath. The embedded server and the
     * full client always have it; a slimmed-down client (mockserver-client-java excludes the Swagger/OpenAPI
     * parser) does not. When absent, the Swagger-coupled serializers in {@link SwaggerSerializers} are not
     * registered — there is never a Swagger object to serialise on a remote client — which keeps the object
     * mapper from failing to initialise with a {@code NoClassDefFoundError}.
     */
    private static final boolean SWAGGER_PRESENT = isClassPresent("io.swagger.v3.oas.models.media.Schema");

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, ObjectMapperFactory.class.getClassLoader());
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static ObjectMapper objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false);
    private static final ObjectWriter prettyPrintWriter = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false).writerWithDefaultPrettyPrinter();
    private static final ObjectWriter prettyPrintWriterThatSerialisesDefaultFields = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), true).writerWithDefaultPrettyPrinter();
    private static final ObjectWriter writer = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false).writer();

    // Cache of ObjectMappers built with extra custom serializers, keyed by the serializer-set
    // signature (the sorted set of serializer concrete classes) plus the serialiseDefaultValues
    // flag. The custom serializers in practice are a tiny, stable set (e.g. JsonNodeExampleSerializer,
    // TimeToLiveDTOPersistenceSerializer), so without a cache every export rebuilt a full ObjectMapper
    // — registering every (de)serializer module — on each call. An ObjectMapper is thread-safe once
    // configured and only read (serialised) afterwards, and the cache is a ConcurrentHashMap built via
    // computeIfAbsent, so reuse is thread-safe.
    private static final java.util.concurrent.ConcurrentMap<String, ObjectMapper> serializerObjectMapperCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static ObjectMapper objectMapperWithSerializers(List<JsonSerializer> additionJsonSerializers, boolean serialiseDefaultValues) {
        // Stable key independent of the JsonSerializer instance identities: the serializers are
        // stateless and selected purely by type, so two calls with the same serializer classes (and
        // the same serialiseDefaultValues flag) are interchangeable.
        String key = serialiseDefaultValues + "|" + additionJsonSerializers.stream()
            .map(serializer -> serializer.getClass().getName())
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
        return serializerObjectMapperCache.computeIfAbsent(key, k ->
            buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), additionJsonSerializers, serialiseDefaultValues));
    }

    public static ObjectMapper createObjectMapper() {
        if (objectMapper == null) {
            objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false);
        }
        return objectMapper;
    }

    public static ObjectMapper createObjectMapper(JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            if (objectMapper == null) {
                objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false);
            }
            return objectMapper;
        } else {
            return objectMapperWithSerializers(Arrays.asList(additionJsonSerializers), false);
        }
    }

    public static ObjectMapper createObjectMapper(JsonDeserializer... replacementJsonDeserializers) {
        if (replacementJsonDeserializers == null || replacementJsonDeserializers.length == 0) {
            if (objectMapper == null) {
                objectMapper = buildObjectMapperWithDeserializerAndSerializers(Collections.emptyList(), Collections.emptyList(), false);
            }
            return objectMapper;
        } else {
            return buildObjectMapperWithDeserializerAndSerializers(Arrays.asList(replacementJsonDeserializers), Collections.emptyList(), false);
        }
    }

    public static ObjectWriter createObjectMapper(boolean pretty, boolean serialiseDefaultValues, JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            if (pretty && serialiseDefaultValues) {
                return prettyPrintWriterThatSerialisesDefaultFields;
            } else if (pretty) {
                return prettyPrintWriter;
            } else {
                return writer;
            }
        } else {
            ObjectMapper mapper = objectMapperWithSerializers(Arrays.asList(additionJsonSerializers), serialiseDefaultValues);
            if (pretty) {
                return mapper.writerWithDefaultPrettyPrinter();
            } else {
                return mapper.writer();
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static ObjectMapper buildObjectMapperWithoutRemovingEmptyValues() {
        JsonFactory jsonFactory = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(100 * 1024 * 1024)
                .build())
            .build();
        ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

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

        // consistent json output
        ExceptionHandling.handleThrowable(() -> objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true));

        return objectMapper;
    }

    public static ObjectMapper buildObjectMapperWithOnlyConfigurationDefaults() {
        ObjectMapper objectMapper = buildObjectMapperWithoutRemovingEmptyValues();

        // remove empty values from JSON
        ExceptionHandling.handleThrowable(() -> objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT));
        ExceptionHandling.handleThrowable(() -> objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL));
        ExceptionHandling.handleThrowable(() -> objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY));

        // add support for java date time serialisation and de-serialisation
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }

    private static ObjectMapper buildObjectMapperWithDeserializerAndSerializers(List<JsonDeserializer> replacementJsonDeserializers, List<JsonSerializer> replacementJsonSerializers, boolean serialiseDefaultValues) {
        ObjectMapper objectMapper = buildObjectMapperWithOnlyConfigurationDefaults();

        // register our own module with our serializers and deserializers
        SimpleModule module = new SimpleModule();
        addDeserializers(module, replacementJsonDeserializers.toArray(new JsonDeserializer[0]));
        addSerializers(module, replacementJsonSerializers.toArray(new JsonSerializer[0]), serialiseDefaultValues);
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
            // client certificate matching criteria
            new ClientCertificateDeserializer(),
            // jwt matching criteria
            new JwtDeserializer(),
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

    private static void addSerializers(SimpleModule module, JsonSerializer[] replacementJsonSerializers, boolean serialiseDefaultValues) {
        List<JsonSerializer> jsonSerializers = new ArrayList<>(Arrays.asList(
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
            new org.mockserver.serialization.serializers.request.ConditionalRequestDefinitionSerializer(),
            new org.mockserver.serialization.serializers.request.ConditionalRequestDefinitionDTOSerializer(),
            new org.mockserver.serialization.serializers.request.AnyOfRequestDefinitionSerializer(),
            new org.mockserver.serialization.serializers.request.AnyOfRequestDefinitionDTOSerializer(),
            // request body
            new BinaryBodySerializer(),
            new BinaryBodyDTOSerializer(),
            new JsonBodySerializer(serialiseDefaultValues),
            new JsonBodyDTOSerializer(serialiseDefaultValues),
            new JsonSchemaBodySerializer(),
            new JsonSchemaBodyDTOSerializer(),
            new JsonPathBodySerializer(),
            new JsonPathBodyDTOSerializer(),
            new ParameterBodySerializer(),
            new ParameterBodyDTOSerializer(),
            new MultipartBodySerializer(),
            new MultipartBodyDTOSerializer(),
            new RegexBodySerializer(),
            new RegexBodyDTOSerializer(),
            new ResponseMatchingBodySerializer(),
            new FuzzyBodySerializer(),
            new FuzzyBodyDTOSerializer(),
            new StringBodySerializer(serialiseDefaultValues),
            new StringBodyDTOSerializer(serialiseDefaultValues),
            new XmlBodySerializer(),
            new XmlBodyDTOSerializer(),
            new XmlSchemaBodySerializer(),
            new XmlSchemaBodyDTOSerializer(),
            new XPathBodySerializer(),
            new XPathBodyDTOSerializer(),
            new JsonRpcBodySerializer(),
            new JsonRpcBodyDTOSerializer(),
            new GraphQLBodySerializer(),
            new GraphQLBodyDTOSerializer(),
            new AllOfBodySerializer(),
            new AllOfBodyDTOSerializer(),
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
            new HttpLlmResponseDTOSerializer(),
            // key and multivalue
            new HeadersSerializer(),
            new ParametersSerializer(),
            new CookiesSerializer(),
            // certificates
            new X509CertificateSerializer(),
            new CertificateSerializer(),
            new ClientCertificateSerializer(),
            new JwtSerializer(),
            // log
            new org.mockserver.serialization.serializers.log.LogEntrySerializer(),
            // matcher
            new HttpRequestPropertiesMatcherSerializer()
        ));
        // Swagger/OpenAPI-coupled serializers (schema serializers + the OpenAPI-derived
        // HttpRequestsPropertiesMatcher serializer) are only registered when swagger-core is on the
        // classpath. A remote client that excludes the OpenAPI parser never produces these objects, so
        // skipping them avoids a NoClassDefFoundError while leaving server behaviour unchanged.
        if (SWAGGER_PRESENT) {
            jsonSerializers.addAll(SwaggerSerializers.swaggerSerializers());
        }
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
