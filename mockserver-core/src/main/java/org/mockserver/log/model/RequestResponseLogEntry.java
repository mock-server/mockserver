package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class RequestResponseLogEntry extends LogEntry {

    private final List<HttpResponse> httpResponses = new ArrayList<>();

    public RequestResponseLogEntry(HttpRequest httpRequest, HttpResponse httpResponse) {
        super(httpRequest);
        addHttpResponse(httpResponse);
    }

    public List<HttpResponse> getHttpResponses() {
        return httpResponses;
    }

    @JsonIgnore
    public List<Expectation> getExpectations() {
        return Lists.transform(httpResponses, new Function<HttpResponse, Expectation>() {
            public Expectation apply(HttpResponse httpResponse) {
                return new Expectation(getHttpRequest(), Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse);
            }
        });
    }

    public RequestResponseLogEntry addHttpResponse(HttpResponse httpResponse) {
        httpResponses.add(httpResponse);
        return this;
    }

    public RequestResponseLogEntry addHttpResponses(List<HttpResponse> httpResponses) {
        httpResponses.addAll(httpResponses);
        return this;
    }

}
