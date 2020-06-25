package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringSerializer extends StdSerializer<NottableString> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public NottableStringSerializer() {
        super(NottableString.class);
    }

    @Override
    public void serialize(NottableString nottableString, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (nottableString instanceof NottableSchemaString) {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(nottableString.getValue());
            if (Boolean.TRUE.equals(nottableString.isNot()) && jsonNode instanceof ObjectNode) {
                ((ObjectNode) jsonNode).put("not", true);
            }
            jgen.writeObject(jsonNode);
        } else {
            jgen.writeString(serialiseNottableString(nottableString));
        }
    }

}
