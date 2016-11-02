package org.mockserver.filters;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.collections.CircularMultiMap;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class RequestResponseLogFilter implements ResponseFilter, RequestFilter {

    // request / response persistence
    private final CircularMultiMap<HttpRequest, HttpResponse> requestResponseLog = new CircularMultiMap<HttpRequest, HttpResponse>(100, 50);
    // matcher
    private final MatcherBuilder matcherBuilder = new MatcherBuilder();
    private Logger requestLogger = LoggerFactory.getLogger("REQUEST");

    @Override
    public /* synchronized */ HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpRequest != null && httpResponse != null) {
            requestResponseLog.put(httpRequest, httpResponse);
        } else if (httpRequest != null) {
            requestResponseLog.put(httpRequest, notFoundResponse());
        }
        return httpResponse;
    }

    @Override
    public /* synchronized */ HttpRequest onRequest(HttpRequest httpRequest) {
        return httpRequest;
    }

    public /* synchronized */ List<HttpResponse> httpResponses(HttpRequest httpRequest) {
        List<HttpResponse> httpResponses = new ArrayList<HttpResponse>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : new LinkedList<HttpRequest>(requestResponseLog.keySet())) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpResponses.addAll(requestResponseLog.getAll(loggedHttpRequest));
            }
        }
        return httpResponses;
    }

    public /* synchronized */ void reset() {
        requestResponseLog.clear();
    }

    public /* synchronized */ void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest key : new LinkedList<HttpRequest>(requestResponseLog.keySet())) {
                if (httpRequestMatcher.matches(key)) {
                    requestResponseLog.removeAll(key);
                }
            }
        } else {
            reset();
        }
    }

    public /* synchronized */ void dumpToLog(HttpRequest httpRequest, boolean asJava) {
        ExpectationSerializer expectationSerializer = new ExpectationSerializer();
        ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (httpRequestMatcher.matches(entry.getKey(), true)) {
                    if (asJava) {
                        requestLogger.warn(expectationToJavaSerializer.serializeAsJava(0, new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue())));
                    } else {
                        requestLogger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue())));
                    }
                }
            }
        } else {
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (asJava) {
                    requestLogger.warn(expectationToJavaSerializer.serializeAsJava(0, new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue())));
                } else {
                    requestLogger.warn(expectationSerializer.serialize(new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue())));
                }
            }
        }
    }

    public /* synchronized */ Expectation[] getExpectations(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        Set<Map.Entry<HttpRequest, HttpResponse>> requestResponseLogEntrySet = requestResponseLog.entrySet();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
                if (httpRequestMatcher.matches(loggedHttpRequest)) {
                    for (HttpResponse loggedHttpResponse : requestResponseLog.getAll(loggedHttpRequest)) {
                        expectations.add(new Expectation(loggedHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(loggedHttpResponse));
                    }
                }
            }
        } else {
            for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
                for (HttpResponse loggedHttpResponse : requestResponseLog.getAll(loggedHttpRequest)) {
                    expectations.add(new Expectation(loggedHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(loggedHttpResponse));
                }
            }
        }
        return expectations.toArray(new Expectation[expectations.size()]);
    }

}
