package org.mockserver.client.serialization.serializers.collections;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public abstract class KeysToMultiValuesSerializer<T extends KeysToMultiValues<? extends KeyToMultiValue, T>> extends StdSerializer<T> {

    KeysToMultiValuesSerializer(Class<T> valueClass) {
        super(valueClass);
    }

    @Override
    public void serialize(T collection, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        for (KeyToMultiValue keyToMultiValue : collection.getEntries()) {
            jgen.writeFieldName(serialiseNottableString(keyToMultiValue.getName()));
            jgen.writeStartArray(keyToMultiValue.getValues().size());
            for (NottableString nottableString : keyToMultiValue.getValues()) {
                jgen.writeString(serialiseNottableString(nottableString));
            }
            jgen.writeEndArray();
        }
    }

}
