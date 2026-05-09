package org.mockserver.serialization.serializers.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.models.media.Schema;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class AbstractSchemaSerializer<T extends Schema> extends StdSerializer<T> {
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.buildObjectMapperWithOnlyConfigurationDefaults();
    private static final List<String> fieldsToRemove = ImmutableList.of(
        "exampleSetFlag",
        "types"
    );

    public AbstractSchemaSerializer(Class<T> type) {
        super(type);
    }

    @Override
    public void serialize(T schema, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        ObjectNode jsonNodes = OBJECT_MAPPER.convertValue(schema, ObjectNode.class);
        recurse(jsonNodes, node -> {
            if (node instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) node;
                JsonNode typesNode = objectNode.get("types");
                if (typesNode != null && typesNode.isArray() && typesNode.size() == 1 && !objectNode.has("type")) {
                    objectNode.put("type", typesNode.get(0).asText());
                }
                normalizeExclusiveBounds(objectNode);
                objectNode.remove(fieldsToRemove);
            }
        });
        jgen.writeObject(jsonNodes);
    }

    private static void normalizeExclusiveBounds(ObjectNode node) {
        normalizeExclusiveBound(node, "exclusiveMinimum", "minimum");
        normalizeExclusiveBound(node, "exclusiveMaximum", "maximum");
    }

    private static void normalizeExclusiveBound(ObjectNode node, String exclusiveField, String boundField) {
        JsonNode exclusiveNode = node.get(exclusiveField);
        if (exclusiveNode != null && exclusiveNode.isBoolean()) {
            if (exclusiveNode.booleanValue()) {
                JsonNode boundNode = node.get(boundField);
                if (boundNode != null && boundNode.isNumber()) {
                    node.set(exclusiveField, boundNode);
                    node.remove(boundField);
                } else {
                    node.remove(exclusiveField);
                }
            } else {
                node.remove(exclusiveField);
            }
        }
    }

    private void recurse(JsonNode node, Consumer<JsonNode> jsonNodeCallable) {
        jsonNodeCallable.accept(node);
        for (JsonNode jsonNode : node) {
            recurse(jsonNode, jsonNodeCallable);
        }
    }
}
