package org.mockserver.serialization.serializers.collections;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeysAndValues;

import java.io.IOException;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public class KeysAndValuesSerializer<KV extends KeysAndValues<? extends KeyAndValue, ?>> extends StdSerializer<KV> {

    private static final long serialVersionUID = 1L;

    public KeysAndValuesSerializer(Class<KV> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(KV collection, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        for (KeyAndValue cookie : collection.getEntries()) {
            jgen.writeStringField(serialiseNottableString(cookie.getName()), serialiseNottableString(cookie.getValue()));
        }
        jgen.writeEndObject();
    }

}
