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
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class LogFilter implements ProxyResponseFilter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public CircularMultiMap<HttpRequest, HttpResponse> requestResponseLog = new CircularMultiMap<>(100, 100);

    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        requestResponseLog.put(httpRequest, httpResponse);
        return httpResponse;
    }

    public List<HttpResponse> httpResponses(HttpRequest httpRequest) {
        List<HttpResponse> httpResponses = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpResponses.addAll(requestResponseLog.getAll(loggedHttpRequest));
            }
        }
        return httpResponses;
    }

    public List<HttpRequest> httpRequests(HttpRequest httpRequest) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpRequests.add(loggedHttpRequest);
            }
        }
        return httpRequests;
    }

    public Expectation[] retrieve(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<>();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
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

    public void reset() {
        requestResponseLog.clear();
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest key : requestResponseLog.keySet()) {
                if (httpRequestMatcher.matches(key)) {
                    requestResponseLog.remove(key);
                }
            }
        } else {
            reset();
        }
    }

    public void dumpToLog(HttpRequest httpRequest) {
        ExpectationSerializer expectationSerializer = new ExpectationSerializer();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = MatcherBuilder.transformsToMatcher(httpRequest);
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (httpRequestMatcher.matches(entry.getKey())) {
                    logger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
                }
            }
        } else {
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                logger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once()).thenRespond(entry.getValue())));
            }
        }
    }
}
