package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * One control-plane response schema of the embedded OpenAPI specification, compared with the JSON the endpoint
 * returns. Field paths are keyed by component name wherever a {@code $ref} is followed, with array items written
 * as {@code []} and map values as {@code {}}, so a shared component is checked once whatever references it.
 */
final class OpenApiResponseSchema {

    private static final String COMPONENT = "#/components/schemas/";
    private static final File SPEC = new File("src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml");

    private final JsonNode spec;
    private final JsonNode schema;
    private final Set<String> returned = new TreeSet<>();

    private OpenApiResponseSchema(JsonNode spec, JsonNode schema) {
        this.spec = spec;
        this.schema = schema;
    }

    static OpenApiResponseSchema of(String path, String status) throws IOException {
        JsonNode spec = new ObjectMapper(new YAMLFactory()).readTree(SPEC);
        JsonNode schema = spec.path("paths").path(path).path("put").path("responses").path(status)
            .path("content").path("application/json").path("schema");
        assertThat("PUT " + path + " documents a " + status + " JSON schema", schema.isObject(), is(true));
        return new OpenApiResponseSchema(spec, schema);
    }

    /**
     * Fails when {@code value} has a field the schema does not name, or a value of another type than documented;
     * records every field path returned.
     */
    void assertDocuments(JsonNode value) {
        assertDocuments(schema, value, "");
    }

    /** The field paths returned so far. */
    Set<String> returned() {
        return returned;
    }

    /** The documented field paths no response has returned so far, leaving out those {@code excluded} accepts. */
    List<String> documentedButNeverReturned(Predicate<String> excluded) {
        Set<String> documented = new TreeSet<>();
        collectDocumented(schema, "", documented);
        return documented.stream().filter(path -> !returned.contains(path) && !excluded.test(path)).collect(Collectors.toList());
    }

    private JsonNode resolve(JsonNode node) {
        if (!node.has("$ref")) {
            return node;
        }
        String ref = node.path("$ref").asText("");
        JsonNode target = ref.startsWith(COMPONENT) ? spec.path("components").path("schemas").path(ref.substring(COMPONENT.length())) : null;
        assertThat("$ref " + ref + " resolves to a component schema", target != null && target.isObject(), is(true));
        return target;
    }

    private static String pathOf(JsonNode node, String path) {
        String ref = node.path("$ref").asText("");
        return ref.startsWith(COMPONENT) ? ref.substring(COMPONENT.length()) : path;
    }

    private void collectDocumented(JsonNode node, String parentPath, Set<String> documented) {
        JsonNode resolved = resolve(node);
        String path = pathOf(node, parentPath);
        resolved.path("properties").fields().forEachRemaining(property -> {
            String child = path + "/" + property.getKey();
            documented.add(child);
            collectDocumented(property.getValue(), child, documented);
        });
        if (resolved.has("items")) {
            collectDocumented(resolved.path("items"), path + "[]", documented);
        }
        if (resolved.path("additionalProperties").isObject()) {
            collectDocumented(resolved.path("additionalProperties"), path + "{}", documented);
        }
    }

    private void assertDocuments(JsonNode node, JsonNode value, String parentPath) {
        JsonNode resolved = resolve(node);
        String path = pathOf(node, parentPath);
        String type = resolved.path("type").asText("");
        if (!type.isEmpty()) {
            String actual = value.isObject() ? "object" : value.isArray() ? "array" : value.isBoolean() ? "boolean" : value.isIntegralNumber() ? "integer" : value.isTextual() ? "string" : value.getNodeType().name();
            assertThat("type of " + path, actual, is(type));
        }
        if (value.isObject() && !type.isEmpty()) {
            value.fields().forEachRemaining(field -> {
                boolean named = resolved.path("properties").has(field.getKey());
                String child = path + (named ? "/" + field.getKey() : "{}");
                assertThat("field " + path + "/" + field.getKey() + " is returned but not in the OpenAPI schema",
                    named || resolved.path("additionalProperties").isObject(), is(true));
                returned.add(child);
                assertDocuments(named ? resolved.path("properties").path(field.getKey()) : resolved.path("additionalProperties"), field.getValue(), child);
            });
        } else if (value.isArray() && !type.isEmpty()) {
            for (JsonNode item : value) {
                assertDocuments(resolved.path("items"), item, path + "[]");
            }
        }
    }
}
