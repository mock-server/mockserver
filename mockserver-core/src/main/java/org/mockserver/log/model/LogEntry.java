package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.ListUtils;
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
        if (httpRequests != null && !httpRequests.isEmpty()) {
            this.httpRequest = httpRequests;
        } else {
            this.httpRequest = ImmutableList.of(request());
        }
    }

    LogEntry(HttpRequest httpRequest) {
        this(ImmutableList.of(httpRequest != null ? httpRequest : request()));
    }

    @JsonIgnore
    public List<HttpRequest> getHttpRequests() {
        return httpRequest;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest.get(0);
    }

}
