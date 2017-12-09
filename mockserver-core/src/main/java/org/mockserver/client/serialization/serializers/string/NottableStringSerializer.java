package org.mockserver.client.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringSerializer extends StdSerializer<NottableString> {

    public NottableStringSerializer() {
        super(NottableString.class);
    }

    @Override
    public void serialize(NottableString nottableString, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeString(serialiseNottableString(nottableString));
    }
}
