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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpRequest != null && httpResponse != null) {
            requestResponseLog.put(httpRequest, httpResponse);
        } else if (httpRequest != null) {
            requestResponseLog.put(httpRequest, notFoundResponse());
        }
        return httpResponse;
    }

    @Override
    public HttpRequest onRequest(HttpRequest httpRequest) {
        return httpRequest;
    }

    public List<HttpResponse> httpResponses(HttpRequest httpRequest) {
        List<HttpResponse> httpResponses = new ArrayList<HttpResponse>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : new LinkedList<HttpRequest>(requestResponseLog.keySet())) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpResponses.addAll(requestResponseLog.getAll(loggedHttpRequest));
            }
        }
        return httpResponses;
    }

    public void reset() {
        requestResponseLog.clear();
    }

    public void clear(HttpRequest httpRequest) {
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

    public void dumpToLog(HttpRequest httpRequest, boolean asJava) {
        ExpectationSerializer expectationSerializer = new ExpectationSerializer();
        ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
        for (Expectation expectation : retrieveExpectations(httpRequest)) {
            if (asJava) {
                requestLogger.info(expectationToJavaSerializer.serializeAsJava(0, expectation));
            } else {
                requestLogger.info(expectationSerializer.serialize(expectation));
            }
        }
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> matchingExpectations = new ArrayList<>();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                if (httpRequestMatcher.matches(entry.getKey(), true)) {
                    matchingExpectations.add(new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue()));
                }
            }
        } else {
            for (Map.Entry<HttpRequest, HttpResponse> entry : requestResponseLog.entrySet()) {
                matchingExpectations.add(new Expectation(entry.getKey(), Times.once(), TimeToLive.unlimited()).thenRespond(entry.getValue()));
            }
        }
        return matchingExpectations;
    }

}
