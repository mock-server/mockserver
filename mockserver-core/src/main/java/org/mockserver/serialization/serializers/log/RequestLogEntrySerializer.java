package org.mockserver.serialization.serializers.log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.log.model.RequestLogEntry;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class RequestLogEntrySerializer extends StdSerializer<RequestLogEntry> {

    public RequestLogEntrySerializer() {
        super(RequestLogEntry.class);
    }

    @Override
    public void serialize(RequestLogEntry requestLogEntry, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (requestLogEntry.getHttpRequest() != null) {
            jgen.writeObjectField("httpRequest", requestLogEntry.getHttpRequest());
        }
        jgen.writeStringField("timestamp", requestLogEntry.getTimestamp());
        jgen.writeEndObject();
    }
}
