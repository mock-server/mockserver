package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.matchers.Times;
import org.mockserver.model.XPathBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class TimesSerializer extends StdSerializer<Times> {

    public TimesSerializer() {
        super(Times.class);
    }

    @Override
    public void serialize(Times times, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (!times.isUnlimited()) {
            jgen.writeNumberField("remainingTimes", times.getRemainingTimes());
        } else {
            jgen.writeBooleanField("unlimited", times.isUnlimited());
        }
        jgen.writeEndObject();
    }
}
