package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public abstract class LogEntry extends ObjectWithJsonToString {

    private static final String[] excludedFields = {"timestamp"};
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final List<HttpRequest> httpRequest;
    private final Date timestamp = new Date();

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

    public String getTimestamp() {
        return dateFormat.format(timestamp);
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
