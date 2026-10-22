package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.AnyOfRequestDefinitionDTO;

import java.io.IOException;

public class AnyOfRequestDefinitionDTOSerializer extends StdSerializer<AnyOfRequestDefinitionDTO> {
    private static final long serialVersionUID = 1L;

    public AnyOfRequestDefinitionDTOSerializer() {
        super(AnyOfRequestDefinitionDTO.class);
    }

    @Override
    public void serialize(AnyOfRequestDefinitionDTO anyOfRequestDefinition, JsonGenerator jgen, SerializerProvider provider) throws IOException {
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
