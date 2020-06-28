package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
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
            writeObject(nottableString, jgen, "schema", OBJECT_MAPPER.readTree(nottableString.getValue()));
        } else if (nottableString.getStyle() != null) {
            writeObject(nottableString, jgen, "value", nottableString.getValue());
        } else {
            jgen.writeString(serialiseNottableString(nottableString));
        }
    }

    private void writeObject(NottableString nottableString, JsonGenerator jgen, String valueFieldName, Object value) throws IOException {
        jgen.writeStartObject();
        if (Boolean.TRUE.equals(nottableString.isNot())) {
            jgen.writeBooleanField("not", true);
        }
        if (Boolean.TRUE.equals(nottableString.isOptional())) {
            jgen.writeBooleanField("optional", true);
        }
        if (nottableString.getStyle() != null) {
            jgen.writeObjectField("style", nottableString.getStyle());
        }
        jgen.writeObjectField(valueFieldName, value);
        jgen.writeEndObject();
    }

}
