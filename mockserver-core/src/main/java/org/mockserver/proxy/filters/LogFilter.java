package org.mockserver.proxy.filters;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.collections.CircularMultiMap;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class LogFilter implements ProxyResponseFilter {

    private final CircularMultiMap<HttpRequest, HttpResponse> requestResponseLog = new CircularMultiMap<>(100, 100);
    private final MatcherBuilder matcherBuilder = new MatcherBuilder();
    private Logger requestLogger = LoggerFactory.getLogger("request");

    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        requestResponseLog.put(httpRequest, httpResponse);
        return httpResponse;
    }

    public List<HttpResponse> httpResponses(HttpRequest httpRequest) {
        List<HttpResponse> httpResponses = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpResponses.addAll(requestResponseLog.getAll(loggedHttpRequest));
            }
        }
        return httpResponses;
    }

    public List<HttpRequest> httpRequests(HttpRequest httpRequest) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpRequests.add(loggedHttpRequest);
            }
        }
        return httpRequests;
    }

    public void reset() {
        requestResponseLog.clear();
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest key : new LinkedHashSet<>(requestResponseLog.keySet())) {
                if (httpRequestMatcher.matches(key)) {
                    requestResponseLog.removeAll(key);
                }
            }
        } else {
            reset();
        }
    }

    public void dumpToLog(HttpRequest httpRequest, boolean asJava) {
        ExpectationSerializer expectationSerializer = new ExpectationSerializer();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (httpRequestMatcher.matches(entry.getKey())) {
                    if (asJava) {
                        requestLogger.warn(expectationSerializer.serializeAsJava(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
                    } else {
                        requestLogger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
                    }
                }
            }
        } else {
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (asJava) {
                    requestLogger.warn(expectationSerializer.serializeAsJava(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
                } else {
                    requestLogger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
                }
            }
        }
    }

    public Expectation[] retrieve(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<>();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (httpRequestMatcher.matches(entry.getKey())) {
                    expectations.add(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue()));
                }
            }
        } else {
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                expectations.add(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue()));
            }
        }
        return expectations.toArray(new Expectation[expectations.size()]);
    }
}
