package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.model.DashboardLogEntryDTOGroup;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class DashboardLogEntryDTOGroupSerializer extends StdSerializer<DashboardLogEntryDTOGroup> {

    public DashboardLogEntryDTOGroupSerializer() {
        super(DashboardLogEntryDTOGroup.class);
    }

    @Override
    public void serialize(DashboardLogEntryDTOGroup logEntryGroup, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        if (logEntryGroup.getLogEntryDTOS().size() > 1) {
            jsonGenerator.writeStartObject();
            DashboardLogEntryDTO firstLogEntry = logEntryGroup.getLogEntryDTOS().get(0);
            jsonGenerator.writeObjectField("key", firstLogEntry.getId() + "_log_group");
            jsonGenerator.writeObjectField("group", new DashboardLogEntryDTO(firstLogEntry.getId(), firstLogEntry.getCorrelationId(), firstLogEntry.getTimestamp(), firstLogEntry.getType()).setDescription(logEntryGroup.getDescriptionProcessor().description(firstLogEntry)));
            jsonGenerator.writeObjectField("value", logEntryGroup.getLogEntryDTOS());
            jsonGenerator.writeEndObject();
        } else if (logEntryGroup.getLogEntryDTOS().size() == 1) {
            jsonGenerator.writeObject(logEntryGroup.getLogEntryDTOS().get(0));
        }
    }
}
