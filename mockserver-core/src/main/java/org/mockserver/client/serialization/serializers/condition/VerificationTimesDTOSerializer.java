package org.mockserver.client.serialization.serializers.condition;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.model.VerificationTimesDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

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
