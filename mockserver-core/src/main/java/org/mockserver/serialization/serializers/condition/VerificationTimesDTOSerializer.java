package org.mockserver.serialization.serializers.condition;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.VerificationTimesDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTOSerializer extends StdSerializer<VerificationTimesDTO> {

    public VerificationTimesDTOSerializer() {
        super(VerificationTimesDTO.class);
    }

    @Override
    public void serialize(VerificationTimesDTO verificationTimesDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (verificationTimesDTO.getAtLeast() != -1) {
            jgen.writeNumberField("atLeast", verificationTimesDTO.getAtLeast());
        }
        if (verificationTimesDTO.getAtMost() != -1) {
            jgen.writeNumberField("atMost", verificationTimesDTO.getAtMost());
        }
        jgen.writeEndObject();
    }
}
