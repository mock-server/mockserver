package org.mockserver.log.model;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;

/**
 * @author jamesdbloom
 */
public abstract class LogEntry extends ObjectWithJsonToString {

    private final HttpRequest httpRequest;

    LogEntry(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

}
