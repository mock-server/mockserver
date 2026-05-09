package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.TimeToLiveDTO;

import java.io.IOException;

public class TimeToLiveDTOPersistenceSerializer extends StdSerializer<TimeToLiveDTO> {
    private static final long serialVersionUID = 1L;

    public TimeToLiveDTOPersistenceSerializer() {
        super(TimeToLiveDTO.class);
    }

    @Override
    public void serialize(TimeToLiveDTO timeToLive, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (!timeToLive.isUnlimited()) {
            jgen.writeObjectField("timeUnit", timeToLive.getTimeUnit());
            jgen.writeNumberField("timeToLive", timeToLive.getTimeToLive());
            if (timeToLive.getEndDate() != null) {
                jgen.writeNumberField("endDate", timeToLive.getEndDate());
            }
        } else {
            jgen.writeBooleanField("unlimited", timeToLive.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
