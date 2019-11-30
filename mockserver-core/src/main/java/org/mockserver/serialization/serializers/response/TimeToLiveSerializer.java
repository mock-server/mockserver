package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.matchers.TimeToLive;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class TimeToLiveSerializer extends StdSerializer<TimeToLive> {

    public TimeToLiveSerializer() {
        super(TimeToLive.class);
    }

    @Override
    public void serialize(TimeToLive timeToLive, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (!timeToLive.isUnlimited()) {
            jgen.writeObjectField("timeUnit", timeToLive.getTimeUnit());
            jgen.writeNumberField("timeToLive", timeToLive.getTimeToLive());
            jgen.writeNumberField("endDate", timeToLive.getEndDate());
        } else {
            jgen.writeBooleanField("unlimited", timeToLive.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
