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
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final String messageFormat;
    private final Level logLevel;
    private final Object[] arguments;
    protected Date timeStamp = new Date();
    private String message;

    public MessageLogEntry(final Level logLevel, final @Nullable HttpRequest httpRequest, final String messageFormat, final Object... arguments) {
        super(httpRequest);
        this.messageFormat = messageFormat;
        this.logLevel = logLevel;
        this.arguments = arguments;
    }

    public MessageLogEntry(final Level logLevel, final @Nullable List<HttpRequest> httpRequests, final String messageFormat, final Object... arguments) {
        super(httpRequests);
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

    public Object[] getArguments() {
        return arguments;
    }

    public String getTimeStamp() {
        return dateFormat.format(timeStamp);
    }
}
