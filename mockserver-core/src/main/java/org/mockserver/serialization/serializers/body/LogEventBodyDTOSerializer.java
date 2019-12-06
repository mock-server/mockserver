package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.LogEventBody;
import org.mockserver.serialization.model.LogEventBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class LogEventBodyDTOSerializer extends StdSerializer<LogEventBodyDTO> {

    public LogEventBodyDTOSerializer() {
        super(LogEventBodyDTO.class);
    }

    @Override
    public void serialize(LogEventBodyDTO logEventBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeObject(logEventBody.getValue());
    }
}
