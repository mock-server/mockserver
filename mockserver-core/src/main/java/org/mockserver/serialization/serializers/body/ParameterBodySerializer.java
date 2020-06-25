package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
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
        if (parameterBody.getOptional() != null && parameterBody.getOptional()) {
            jgen.writeBooleanField("optional", parameterBody.getOptional());
        }
        jgen.writeStringField("type", parameterBody.getType().name());
        if (!parameterBody.getValue().isEmpty()) {
            jgen.writeObjectField("value", parameterBody.getValue());
        }
        jgen.writeEndObject();
    }
}
