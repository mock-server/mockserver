package org.mockserver.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.openapi.examples.ExampleBuilder;
import org.mockserver.openapi.examples.JsonNodeExampleSerializer;
import org.mockserver.openapi.examples.models.Example;
import org.mockserver.openapi.examples.models.StringExample;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Synthesizes a schema-valid response body from a plain (inline) JSON Schema, without requiring a
 * full OpenAPI specification to be attached to the expectation. This is the JSON-Schema analogue of
 * the OpenAPI response-generation path: the inline schema is wrapped in a minimal OpenAPI document,
 * parsed with the same {@link OpenAPIParser} (so {@code $ref}, {@code allOf}, OpenAPI 3.1 type arrays
 * and the typed swagger {@code Schema} subclasses are produced exactly as for a real spec), and then
 * handed to the existing {@link ExampleBuilder}/{@code SampleDataGenerator} engine to produce a body
 * that respects types, {@code required}, {@code enum}, {@code default} and arrays.
 *
 * <p>No example-generation logic is reimplemented here — only the plumbing that turns a bare JSON
 * Schema into the typed schema the engine already understands.
 *
 * <p><b>Trust model:</b> the inline schema is parsed via the same {@link OpenAPIParser} used for full
 * OpenAPI specs, so a remote or {@code file:} {@code $ref} inside the schema is resolved (fetched) exactly
 * as it would be for an attached spec. This deliberately follows the OpenAPI-spec trust model — the field
 * is settable only via the (JWT-protectable) control plane, which already permits attaching an arbitrary
 * spec via {@code specUrlOrPayload} — and is <i>not</i> gated by the {@code jsonSchemaAllowRemoteRefs}
 * property that guards remote {@code $ref}s on the JSON-Schema body-<i>matching</i> path. Treat an inline
 * {@code generateFromSchema} with the same trust as a {@code specUrlOrPayload}.
 */
@SuppressWarnings("rawtypes")
public class JsonSchemaResponseSynthesizer {

    // the component name the inline schema is mounted under inside the synthetic OpenAPI document
    static final String ROOT_SCHEMA_NAME = "GeneratedResponse";

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private static final ObjectWriter EXAMPLE_WRITER =
        ObjectMapperFactory.createObjectMapper(new JsonNodeExampleSerializer()).writer();

    private final MockServerLogger mockServerLogger;
    private final Configuration configuration;

    public JsonSchemaResponseSynthesizer(MockServerLogger mockServerLogger) {
        this(mockServerLogger, null);
    }

    /**
     * @param configuration whose forwardProxyBlockPrivateNetworks applies to a schema's remote $refs; null for the global properties
     */
    public JsonSchemaResponseSynthesizer(MockServerLogger mockServerLogger, Configuration configuration) {
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
    }

    /**
     * Generates a schema-valid JSON body for the supplied inline JSON Schema.
     *
     * @param jsonSchema the inline JSON Schema (a plain JSON Schema object, not a full OpenAPI doc)
     * @return a schema-valid JSON body, or {@code null} when the schema is blank or no example could
     * be generated (the caller then leaves the response body unset)
     */
    public String synthesizeResponse(String jsonSchema) {
        if (!isNotBlank(jsonSchema)) {
            return null;
        }
        try {
            OpenAPI openAPI = OpenAPIParser.buildOpenAPIForInlineJsonSchema(wrapInOpenAPIDocument(jsonSchema), mockServerLogger, configuration);
            Map<String, Schema> schemas = openAPI.getComponents() != null ? openAPI.getComponents().getSchemas() : null;
            if (schemas == null || !schemas.containsKey(ROOT_SCHEMA_NAME)) {
                return null;
            }
            Schema<?> rootSchema = schemas.get(ROOT_SCHEMA_NAME);
            Example example = ExampleBuilder.fromSchema(rootSchema, schemas, null, ExampleBuilder.Direction.RESPONSE);
            if (example == null) {
                return null;
            }
            if (example instanceof StringExample stringExample) {
                return EXAMPLE_WRITER.writeValueAsString(stringExample.getValue());
            }
            return EXAMPLE_WRITER.writeValueAsString(example);
        } catch (Throwable throwable) {
            throw new JsonSchemaResponseSynthesisException("unable to generate a schema-valid response from the supplied JSON schema", throwable);
        }
    }

    /**
     * Wraps the inline JSON Schema in a minimal OpenAPI 3.0 document so the existing parser yields the
     * typed swagger {@link Schema} the example engine expects. The schema is embedded as a parsed JSON
     * node (not string-interpolated) so any quoting inside the user's schema is preserved.
     */
    static String wrapInOpenAPIDocument(String jsonSchema) throws Exception {
        JsonNode schemaNode = OBJECT_MAPPER.readTree(jsonSchema);

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("openapi", "3.0.0");
        ObjectNode info = root.putObject("info");
        info.put("title", "inline json schema response generation");
        info.put("version", "1.0.0");
        // an empty paths object keeps the document structurally valid
        root.putObject("paths");
        ObjectNode components = root.putObject("components");
        ObjectNode componentSchemas = components.putObject("schemas");
        componentSchemas.set(ROOT_SCHEMA_NAME, schemaNode);

        return OBJECT_MAPPER.writeValueAsString(root);
    }
}
