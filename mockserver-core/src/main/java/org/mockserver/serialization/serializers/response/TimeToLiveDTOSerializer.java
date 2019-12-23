package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.TimeToLiveDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDTOSerializer extends StdSerializer<TimeToLiveDTO> {

    public TimeToLiveDTOSerializer() {
        super(TimeToLiveDTO.class);
    }

    @Override
    public void serialize(TimeToLiveDTO timeToLive, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (!timeToLive.isUnlimited()) {
            jgen.writeObjectField("timeUnit", timeToLive.getTimeUnit());
            jgen.writeNumberField("timeToLive", timeToLive.getTimeToLive());
        } else {
            jgen.writeBooleanField("unlimited", timeToLive.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
