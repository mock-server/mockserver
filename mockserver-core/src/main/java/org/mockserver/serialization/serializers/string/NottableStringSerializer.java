package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.mockserver.model.NottableString.NOT_CHAR;
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
        if (nottableString instanceof NottableSchemaString && !nottableString.isNot()) {
            jgen.writeObject(OBJECT_MAPPER.readTree(nottableString.getValue()));
        } else {
            jgen.writeString(serialiseNottableString(nottableString));
        }
    }

}
