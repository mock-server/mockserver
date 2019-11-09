package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

public class ThrowableSerializer extends StdSerializer<Throwable> {
    public ThrowableSerializer() {
        super(Throwable.class);
    }

    @Override
    public void serialize(final Throwable value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        final String stackTrace = getStackTrace(value);
        final String[] lines = stackTrace.split("\n");
        if (lines.length > 1) {
            gen.writeObject(lines);
        } else {
            gen.writeString(stackTrace);
        }
    }
}