package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.LogEntryBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class LogEntryBodyDTOSerializer extends StdSerializer<LogEntryBodyDTO> {

    private static final long serialVersionUID = 1L;

    public LogEntryBodyDTOSerializer() {
        super(LogEntryBodyDTO.class);
    }

    @Override
    public void serialize(LogEntryBodyDTO logEventBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeObject(logEventBody.getValue());
    }
}
