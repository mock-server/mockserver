package org.mockserver.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link JsonSchemaResponseSynthesizer} generates schema-valid bodies from a plain inline
 * JSON Schema by reusing the OpenAPI example-generation engine.
 */
public class JsonSchemaResponseSynthesizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchemaResponseSynthesizer synthesizer = new JsonSchemaResponseSynthesizer(new MockServerLogger(JsonSchemaResponseSynthesizerTest.class));

    @Test
    public void shouldGenerateScalarStringValue() throws Exception {
        // when
        String body = synthesizer.synthesizeResponse("{\"type\":\"string\"}");

        // then - a bare JSON string
        JsonNode node = MAPPER.readTree(body);
        assertTrue("scalar string", node.isTextual());
    }

    @Test
    public void shouldHonourTypesRequiredEnumDefaultArrayAndNestedObject() throws Exception {
        // given - a single object schema exercising the required generation features
        String schema = "{" +
            "\"type\":\"object\"," +
            "\"required\":[\"id\",\"status\",\"active\"]," +
            "\"properties\":{" +
            "\"id\":{\"type\":\"integer\"}," +
            "\"score\":{\"type\":\"number\"}," +
            "\"active\":{\"type\":\"boolean\"}," +
            "\"status\":{\"type\":\"string\",\"enum\":[\"OPEN\",\"CLOSED\"]}," +
            "\"role\":{\"type\":\"string\",\"default\":\"admin\"}," +
            "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"minItems\":2}," +
            "\"owner\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"}}}" +
            "}}";

        // when
        JsonNode body = MAPPER.readTree(synthesizer.synthesizeResponse(schema));

        // then - types
        assertTrue("integer", body.get("id").isInt());
        assertTrue("number", body.get("score").isNumber());
        assertTrue("boolean", body.get("active").isBoolean());
        // enum -> first value
        assertThat(body.get("status").asText(), is("OPEN"));
        // default -> declared default
        assertThat(body.get("role").asText(), is("admin"));
        // array honours minItems and item type
        assertTrue("array", body.get("tags").isArray());
        assertThat(body.get("tags").size(), is(greaterThanOrEqualTo(2)));
        assertTrue("array item string", body.get("tags").get(0).isTextual());
        // nested object
        assertTrue("nested object", body.get("owner").isObject());
        assertTrue("nested string", body.get("owner").get("name").isTextual());
        assertTrue("nested integer", body.get("owner").get("age").isInt());
    }

    @Test
    public void shouldGenerateArrayOfObjectsAtRoot() throws Exception {
        // given - a root array schema of objects
        String schema = "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"sku\":{\"type\":\"string\"}}}}";

        // when
        JsonNode body = MAPPER.readTree(synthesizer.synthesizeResponse(schema));

        // then - a JSON array of objects
        assertTrue("root array", body.isArray());
        assertTrue("array element object", body.get(0).isObject());
        assertTrue("sku string", body.get(0).get("sku").isTextual());
    }

    @Test
    public void shouldReturnNullForBlankSchema() {
        assertThat(synthesizer.synthesizeResponse("   "), is(nullValue()));
        assertThat(synthesizer.synthesizeResponse(null), is(nullValue()));
    }

    @Test(expected = JsonSchemaResponseSynthesisException.class)
    public void shouldThrowSynthesisExceptionForUnparseableSchema() {
        synthesizer.synthesizeResponse("this is not valid json <<<");
    }

    // valid JSON Schema that breaks OpenAPI 3.0 rules; the unique description keeps it out of the shared parse cache
    private static String jsonSchemaOutsideOpenAPI30() {
        return "{" +
            "\"$schema\":\"http://json-schema.org/draft-07/schema#\"," +
            "\"description\":\"" + UUID.randomUUID() + "\"," +
            "\"type\":\"object\"," +
            "\"properties\":{" +
            "\"kind\":{\"const\":\"pet\"}," +
            "\"name\":{\"type\":[\"string\",\"null\"]}" +
            "}}";
    }

    private static List<LogEntry> logged(MockServerLogger logger, Level level) {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream().filter(entry -> entry.getLogLevel() == level).collect(Collectors.toList());
    }

    private static MockServerLogger loggerEnabledForEveryLevel() {
        MockServerLogger logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any())).thenReturn(true);
        return logger;
    }

    @Test
    public void shouldNotWarnAboutAnOpenAPISpecForAnInlineJsonSchema() throws Exception {
        MockServerLogger logger = loggerEnabledForEveryLevel();

        JsonNode body = MAPPER.readTree(new JsonSchemaResponseSynthesizer(logger).synthesizeResponse(jsonSchemaOutsideOpenAPI30()));

        assertTrue("object", body.isObject());
        assertThat(logged(logger, Level.WARN), is(empty()));
        List<LogEntry> debug = logged(logger, Level.DEBUG);
        assertThat(debug, hasSize(1));
        assertThat(debug.get(0).getMessageFormat(), containsString("inline JSON Schema"));
        assertThat((String) debug.get(0).getArguments()[0], containsString("#/components/schemas/GeneratedResponse/"));
    }

    @Test
    public void shouldStillWarnWhenTheSameDocumentIsLoadedAsAnOpenAPISpec() throws Exception {
        String jsonSchema = jsonSchemaOutsideOpenAPI30();
        MockServerLogger synthesizerLogger = loggerEnabledForEveryLevel();
        MockServerLogger specLogger = loggerEnabledForEveryLevel();

        new JsonSchemaResponseSynthesizer(synthesizerLogger).synthesizeResponse(jsonSchema);
        OpenAPIParser.buildOpenAPI(JsonSchemaResponseSynthesizer.wrapInOpenAPIDocument(jsonSchema), specLogger, null);

        assertThat(logged(synthesizerLogger, Level.WARN), is(empty()));
        List<LogEntry> warnings = logged(specLogger, Level.WARN);
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0).getMessageFormat(), containsString("loaded OpenAPI spec with problems"));
    }
}
