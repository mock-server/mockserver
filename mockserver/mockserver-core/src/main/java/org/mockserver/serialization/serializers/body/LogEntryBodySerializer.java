package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.LogEntryBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class LogEntryBodySerializer extends StdSerializer<LogEntryBody> {

    private static final long serialVersionUID = 1L;

    public LogEntryBodySerializer() {
        super(LogEntryBody.class);
    }

    @Override
    public void serialize(LogEntryBody logEventBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeObject(logEventBody.getValue());
    }
}
