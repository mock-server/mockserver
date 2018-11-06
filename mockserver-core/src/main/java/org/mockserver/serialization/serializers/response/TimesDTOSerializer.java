package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.TimesDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class TimesDTOSerializer extends StdSerializer<TimesDTO> {

    public TimesDTOSerializer() {
        super(TimesDTO.class);
    }

    @Override
    public void serialize(TimesDTO times, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (!times.isUnlimited()) {
            jgen.writeNumberField("remainingTimes", times.getRemainingTimes());
        } else {
            jgen.writeBooleanField("unlimited", times.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
