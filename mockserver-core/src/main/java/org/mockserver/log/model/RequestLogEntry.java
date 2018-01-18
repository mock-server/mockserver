package org.mockserver.log.model;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class RequestLogEntry extends LogEntry {
    public RequestLogEntry(HttpRequest httpRequest) {
        super(httpRequest);
    }
}
