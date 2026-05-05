package org.mockserver.serialization.serializers.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.swagger.v3.oas.models.media.PasswordSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonSchemaBodyDTO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class SchemaSerializer extends AbstractSchemaSerializer<Schema> {

    public SchemaSerializer() {
        super(Schema.class);
    }

}

