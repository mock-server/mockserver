package org.mockserver.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.mockserver.log.model.LogEntry;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.serialization.deserializers.body.BodyDTODeserializer;
import org.mockserver.serialization.deserializers.body.BodyWithContentTypeDTODeserializer;
import org.mockserver.serialization.deserializers.collections.CookiesDeserializer;
import org.mockserver.serialization.deserializers.collections.HeadersDeserializer;
import org.mockserver.serialization.deserializers.collections.ParametersDeserializer;
import org.mockserver.serialization.deserializers.condition.TimeToLiveDTODeserializer;
import org.mockserver.serialization.deserializers.condition.VerificationTimesDTODeserializer;
import org.mockserver.serialization.deserializers.string.NottableStringDeserializer;
import org.mockserver.serialization.model.*;
import org.mockserver.serialization.serializers.body.*;
import org.mockserver.serialization.serializers.collections.CookiesSerializer;
import org.mockserver.serialization.serializers.collections.HeadersSerializer;
import org.mockserver.serialization.serializers.collections.ParametersSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesDTOSerializer;
import org.mockserver.serialization.serializers.condition.VerificationTimesSerializer;
import org.mockserver.serialization.serializers.request.HttpRequestDTOSerializer;
import org.mockserver.serialization.serializers.response.*;
import org.mockserver.serialization.serializers.response.HttpResponseSerializer;
import org.mockserver.serialization.serializers.string.NottableStringSerializer;
import org.mockserver.verify.VerificationTimes;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactory {

    private static final ObjectMapper OBJECT_MAPPER = buildObjectMapper();

    public static ObjectMapper createObjectMapper(JsonSerializer... additionJsonSerializers) {
        if (additionJsonSerializers == null || additionJsonSerializers.length == 0) {
            return OBJECT_MAPPER;
        } else {
            return buildObjectMapper(additionJsonSerializers);
        }
    }

    private static ObjectMapper buildObjectMapper(JsonSerializer... additionJsonSerializers) {
        ObjectMapper objectMapper = new ObjectMapper();

        // ignore failures
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // relax parsing
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_MISSING_VALUES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
        objectMapper.configure(JsonParser.Feature.IGNORE_UNDEFINED, true);

        // use arrays
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);

        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        // register our own module with our serializers and deserializers
        Module module = new Module();
        for (JsonSerializer additionJsonSerializer : additionJsonSerializers) {
            module.addSerializer(additionJsonSerializer);
        }
        objectMapper.registerModule(module);
        return objectMapper;
    }

    private static class Module extends SimpleModule {

        Module() {
            // times
            addSerializer(Times.class, new TimesSerializer());
            addSerializer(TimesDTO.class, new TimesDTOSerializer());
            addSerializer(TimeToLiveDTO.class, new TimeToLiveDTOSerializer());
            addDeserializer(TimeToLiveDTO.class, new TimeToLiveDTODeserializer());
            // request
            addSerializer(HttpRequest.class, new org.mockserver.serialization.serializers.request.HttpRequestSerializer());
            addSerializer(HttpRequestDTO.class, new HttpRequestDTOSerializer());
            // request body
            addDeserializer(BodyDTO.class, new BodyDTODeserializer());
            addDeserializer(BodyWithContentTypeDTO.class, new BodyWithContentTypeDTODeserializer());
            addSerializer(BinaryBody.class, new BinaryBodySerializer());
            addSerializer(BinaryBodyDTO.class, new BinaryBodyDTOSerializer());
            addSerializer(JsonBody.class, new JsonBodySerializer());
            addSerializer(JsonBodyDTO.class, new JsonBodyDTOSerializer());
            addSerializer(JsonSchemaBody.class, new JsonSchemaBodySerializer());
            addSerializer(JsonSchemaBodyDTO.class, new JsonSchemaBodyDTOSerializer());
            addSerializer(JsonPathBody.class, new JsonPathBodySerializer());
            addSerializer(JsonPathBodyDTO.class, new JsonPathBodyDTOSerializer());
            addSerializer(ParameterBody.class, new ParameterBodySerializer());
            addSerializer(ParameterBodyDTO.class, new ParameterBodyDTOSerializer());
            addSerializer(RegexBody.class, new RegexBodySerializer());
            addSerializer(RegexBodyDTO.class, new RegexBodyDTOSerializer());
            addSerializer(StringBody.class, new StringBodySerializer());
            addSerializer(StringBodyDTO.class, new StringBodyDTOSerializer());
            addSerializer(XmlBody.class, new XmlBodySerializer());
            addSerializer(XmlBodyDTO.class, new XmlBodyDTOSerializer());
            addSerializer(XmlSchemaBody.class, new XmlSchemaBodySerializer());
            addSerializer(XmlSchemaBodyDTO.class, new XmlSchemaBodyDTOSerializer());
            addSerializer(XPathBody.class, new XPathBodySerializer());
            addSerializer(XPathBodyDTO.class, new XPathBodyDTOSerializer());
            // condition
            addDeserializer(VerificationTimesDTO.class, new VerificationTimesDTODeserializer());
            addSerializer(VerificationTimesDTO.class, new VerificationTimesDTOSerializer());
            addSerializer(VerificationTimes.class, new VerificationTimesSerializer());
            // nottable string
            addSerializer(NottableString.class, new NottableStringSerializer());
            addDeserializer(NottableString.class, new NottableStringDeserializer());
            // response
            addSerializer(HttpResponse.class, new HttpResponseSerializer());
            addSerializer(HttpResponseDTO.class, new HttpResponseDTOSerializer());
            // key and multivalue
            addDeserializer(Headers.class, new HeadersDeserializer());
            addSerializer(Headers.class, new HeadersSerializer());
            addDeserializer(Parameters.class, new ParametersDeserializer());
            addSerializer(Parameters.class, new ParametersSerializer());
            addDeserializer(Cookies.class, new CookiesDeserializer());
            addSerializer(Cookies.class, new CookiesSerializer());
            // log
            addSerializer(LogEntry.class, new org.mockserver.serialization.serializers.log.LogEntrySerializer());
        }

    }

}
