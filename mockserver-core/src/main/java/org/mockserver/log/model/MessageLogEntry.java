package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.mockserver.formatting.StringFormatter.formatLogMessage;


/**
 * @author jamesdbloom
 */
public class MessageLogEntry extends LogEntry {

    private final LogMessageType type;
    private final String messageFormat;
    private final Level logLevel;
    private final Object[] arguments;
    private String message;

    public MessageLogEntry(final MessageLogEntry.LogMessageType type, final Level logLevel, final @Nullable HttpRequest httpRequest, final String messageFormat, final Object... arguments) {
        super(httpRequest);
        this.type = type;
        this.messageFormat = messageFormat;
        this.logLevel = logLevel;
        this.arguments = arguments;
    }

    public MessageLogEntry(final MessageLogEntry.LogMessageType type, final Level logLevel, final @Nullable List<HttpRequest> httpRequests, final String messageFormat, final Object... arguments) {
        super(httpRequests);
        this.type = type;
        this.messageFormat = messageFormat;
        this.logLevel = logLevel;
        this.arguments = arguments;
    }

    @JsonIgnore
    public String getMessage() {
        if (message == null) {
            message = formatLogMessage(messageFormat, arguments);
        }
        return message;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public LogMessageType getType() {
        return type;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public static enum LogMessageType {
        TRACE,
        CLEARED,
        RETRIEVED,
        CREATED_EXPECTATION,
        EXPECTATION_RESPONSE,
        EXPECTATION_MATCHED,
        EXPECTATION_NOT_MATCHED,
        VERIFICATION,
        VERIFICATION_FAILED,
        FORWARDED_REQUEST,
        TEMPLATE_GENERATED,
        SERVER_CONFIGURATION,
        WARN,
        EXCEPTION,
    }
}
