package org.mockserver.log.model;

import com.google.common.collect.ImmutableList;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;

import java.util.Arrays;
import java.util.List;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public abstract class LogEntry extends ObjectWithJsonToString {

    private final List<HttpRequest> httpRequest;

    LogEntry(List<HttpRequest> httpRequests) {
        if (httpRequests != null) {
            this.httpRequest = httpRequests;
        } else {
            this.httpRequest = ImmutableList.of(request());
        }
    }

    LogEntry(HttpRequest httpRequest) {
        this(ImmutableList.of(httpRequest != null ? httpRequest : request()));
    }

    public List<HttpRequest> getHttpRequests() {
        return httpRequest;
    }

}
