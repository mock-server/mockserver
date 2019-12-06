package org.mockserver.serialization.serializers.log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.log.model.LogEntry;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class LogEntrySerializer extends StdSerializer<LogEntry> {

    public LogEntrySerializer() {
        super(LogEntry.class);
    }

    @Override
    public void serialize(LogEntry logEntry, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        // jgen.writeObjectField("key", Long.hashCode(logEntry.getEpochTime()));
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
            if (logEntry.getHttpRequests().length > 1) {
                jgen.writeObjectField("httpRequests", logEntry.getHttpUpdatedRequests());
            } else if (logEntry.getHttpRequests().length == 1) {
                jgen.writeObjectField("httpRequest", logEntry.getHttpUpdatedRequests()[0]);
            }
        }
        if (logEntry.getHttpResponse() != null) {
            jgen.writeObjectField("httpResponse", logEntry.getHttpUpdatedResponse());
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
        if (logEntry.getThrowable() != null) {
            jgen.writeObjectField("throwable", logEntry.getThrowable());
        }
        jgen.writeEndObject();
    }
}
