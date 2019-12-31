package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class DashboardLogEntryDTOSerializer extends StdSerializer<DashboardLogEntryDTO> {

    public DashboardLogEntryDTOSerializer() {
        super(DashboardLogEntryDTO.class);
    }

    @Override
    public void serialize(DashboardLogEntryDTO logEntry, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        jgen.writeObjectField("key", logEntry.getId());
        jgen.writeObjectFieldStart("value");
        if (logEntry.getLogLevel() != null) {
            jgen.writeObjectField("logLevel", logEntry.getLogLevel());
        }
        if (logEntry.getTimestamp() != null) {
            jgen.writeObjectField("timestamp", logEntry.getTimestamp());
        }
        if (logEntry.getType() != null) {
            jgen.writeObjectField("type", logEntry.getType());
        }
        if (logEntry.getHttpRequests() != null) {
            jgen.writeObjectField("httpRequests", logEntry.getHttpRequests());
        }
        if (logEntry.getHttpResponse() != null) {
            jgen.writeObjectField("httpResponse", logEntry.getHttpResponse());
        }
        if (logEntry.getHttpError() != null) {
            jgen.writeObjectField("httpError", logEntry.getHttpError());
        }
        if (logEntry.getExpectation() != null) {
            jgen.writeObjectField("expectation", logEntry.getExpectation());
        }
        if (logEntry.getMessage() != null) {
            jgen.writeObjectField("message", logEntry.getMessage().replaceAll("\t", "   ").split("\n"));
        }
        if (logEntry.getMessage() != null) {
            jgen.writeObjectField("messageFormat", logEntry.getMessageFormat());
        }
        if (logEntry.getMessage() != null) {
            jgen.writeObjectField("arguments", logEntry.getArguments());
        }
        if (logEntry.getThrowable() != null) {
            jgen.writeObjectField("throwable", logEntry.getThrowable());
        }
        jgen.writeEndObject();
        jgen.writeEndObject();
    }
}
