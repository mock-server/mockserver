package org.mockserver.log.model;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MessageLogEntry extends LogEntry {
    private final String message;

    public MessageLogEntry(HttpRequest httpRequest, String message) {
        super(httpRequest);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
