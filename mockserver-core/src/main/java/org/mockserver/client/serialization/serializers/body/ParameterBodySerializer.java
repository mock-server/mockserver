package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ParameterBodySerializer extends StdSerializer<ParameterBody> {

    public ParameterBodySerializer() {
        super(ParameterBody.class);
    }

    @Override
    public void serialize(ParameterBody parameterBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (parameterBody.getNot() != null && parameterBody.getNot()) {
            jgen.writeBooleanField("not", parameterBody.getNot());
        }
        jgen.writeStringField("type", parameterBody.getType().name());
        if (!parameterBody.getValue().isEmpty()) {
            jgen.writeArrayFieldStart("value");
            for (Parameter parameter : parameterBody.getValue()) {
                jgen.writeStartObject();
                if (parameter.getNot() != null && parameter.getNot()) {
                    jgen.writeBooleanField("not", parameter.getNot());
                }
                jgen.writeStringField("name", parameter.getName());
                jgen.writeObjectField("values", parameter.getValues());
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
        }
        jgen.writeEndObject();
    }
}
