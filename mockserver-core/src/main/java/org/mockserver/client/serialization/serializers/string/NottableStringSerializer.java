package org.mockserver.client.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.NottableString;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class NottableStringSerializer extends StdSerializer<NottableString> {

    public NottableStringSerializer() {
        super(NottableString.class);
    }

    @Override
    public void serialize(NottableString nottableString, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (nottableString.getNot() != null && nottableString.getNot()) {
            jgen.writeStartObject();
            jgen.writeBooleanField("not", true);
            jgen.writeStringField("value", nottableString.getValue());
            jgen.writeEndObject();
        } else {
            jgen.writeString(nottableString.getValue());
        }
    }
}
