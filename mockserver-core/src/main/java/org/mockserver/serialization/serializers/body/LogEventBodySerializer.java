package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.LogEventBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class LogEventBodySerializer extends StdSerializer<LogEventBody> {

    public LogEventBodySerializer() {
        super(LogEventBody.class);
    }

    @Override
    public void serialize(LogEventBody logEventBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeObject(logEventBody.getValue());
    }
}
