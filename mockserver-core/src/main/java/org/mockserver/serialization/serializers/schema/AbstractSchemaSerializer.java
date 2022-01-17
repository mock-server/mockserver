package org.mockserver.serialization.serializers.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
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

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.buildObjectMapperWithOnlyConfigurationDefaults();
    private static final List<String> fieldsToRemove = Collections.singletonList(
        "exampleSetFlag"
    );

    public AbstractSchemaSerializer(Class<T> type) {
        super(type);
    }

    @Override
    public void serialize(T schema, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        ObjectNode jsonNodes = OBJECT_MAPPER.convertValue(schema, ObjectNode.class);
        recurse(jsonNodes, node -> {
            if (node instanceof ObjectNode) {
                ((ObjectNode) node).remove(fieldsToRemove);
            }
        });
        jgen.writeObject(jsonNodes);
    }

    private void recurse(JsonNode node, Consumer<JsonNode> jsonNodeCallable) {
        jsonNodeCallable.accept(node);
        for (JsonNode jsonNode : node) {
            recurse(jsonNode, jsonNodeCallable);
        }
    }
}
