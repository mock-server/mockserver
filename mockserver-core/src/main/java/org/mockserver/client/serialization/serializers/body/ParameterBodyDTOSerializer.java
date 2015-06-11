package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.ParameterBodyDTO;
import org.mockserver.client.serialization.model.ParameterDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTOSerializer extends StdSerializer<ParameterBodyDTO> {

    public ParameterBodyDTOSerializer() {
        super(ParameterBodyDTO.class);
    }

    @Override
    public void serialize(ParameterBodyDTO parameterBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (parameterBodyDTO.getNot() != null && parameterBodyDTO.getNot()) {
            jgen.writeBooleanField("not", parameterBodyDTO.getNot());
        }
        jgen.writeStringField("type", parameterBodyDTO.getType().name());
        if (!parameterBodyDTO.getParameters().isEmpty()) {
            jgen.writeArrayFieldStart("parameters");
            for (ParameterDTO parameter : parameterBodyDTO.getParameters()) {
                jgen.writeStartObject();
                jgen.writeObjectField("name", parameter.getName());
                jgen.writeObjectField("values", parameter.getValues());
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
        }
        jgen.writeEndObject();
    }
}
