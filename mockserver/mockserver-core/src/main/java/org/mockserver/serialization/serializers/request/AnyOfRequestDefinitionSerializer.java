package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.AnyOfRequestDefinition;

import java.io.IOException;

public class AnyOfRequestDefinitionSerializer extends StdSerializer<AnyOfRequestDefinition> {
    private static final long serialVersionUID = 1L;

    public AnyOfRequestDefinitionSerializer() {
        super(AnyOfRequestDefinition.class);
    }

    @Override
    public void serialize(AnyOfRequestDefinition anyOfRequestDefinition, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (anyOfRequestDefinition.getNot() != null && anyOfRequestDefinition.getNot()) {
            jgen.writeBooleanField("not", anyOfRequestDefinition.getNot());
        }
        if (anyOfRequestDefinition.getAnyOf() != null) {
            jgen.writeObjectField("anyOf", anyOfRequestDefinition.getAnyOf());
        }
        jgen.writeEndObject();
    }
}
