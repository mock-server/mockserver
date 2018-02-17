package org.mockserver.log.model;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public abstract class LogEntry extends ObjectWithJsonToString {

    private final HttpRequest httpRequest;

    LogEntry(HttpRequest httpRequest) {
        if (httpRequest != null) {
            this.httpRequest = httpRequest;
        } else {
            this.httpRequest = request();
        }
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

}
