package org.mockserver.filters;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.collections.CircularLinkedList;
import org.mockserver.collections.CircularMultiMap;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class LogFilter implements ResponseFilter, RequestFilter {

    private final CircularMultiMap<HttpRequest, HttpResponse> requestResponseLog = new CircularMultiMap<HttpRequest, HttpResponse>(100, 100);
    private final CircularLinkedList<HttpRequest> requestLog = new CircularLinkedList<HttpRequest>(100);
    private final MatcherBuilder matcherBuilder = new MatcherBuilder();
    private Logger requestLogger = LoggerFactory.getLogger("REQUEST");

    @Override
    public synchronized HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpRequest != null && httpResponse != null) {
            requestResponseLog.put(httpRequest, httpResponse);
        } else if (httpRequest != null) {
            requestResponseLog.put(httpRequest, notFoundResponse());
        }
        return httpResponse;
    }

    @Override
    public synchronized HttpRequest onRequest(HttpRequest httpRequest) {
        requestLog.add(httpRequest);
        return httpRequest;
    }

    public synchronized List<HttpResponse> httpResponses(HttpRequest httpRequest) {
        List<HttpResponse> httpResponses = new ArrayList<HttpResponse>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestResponseLog.keySet()) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpResponses.addAll(requestResponseLog.getAll(loggedHttpRequest));
            }
        }
        return httpResponses;
    }

    public synchronized List<HttpRequest> httpRequests(HttpRequest httpRequest) {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestLog) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpRequests.add(loggedHttpRequest);
            }
        }
        return httpRequests;
    }

    public synchronized void reset() {
        requestResponseLog.clear();
        requestLog.clear();
    }

    public synchronized void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest key : new LinkedList<HttpRequest>(requestResponseLog.keySet())) {
                if (httpRequestMatcher.matches(key)) {
                    requestResponseLog.removeAll(key);
                }
            }
            for (HttpRequest value : new LinkedList<HttpRequest>(requestLog)) {
                if (httpRequestMatcher.matches(value)) {
                    requestLog.remove(value);
                }
            }
        } else {
            reset();
        }
    }

    public synchronized void dumpToLog(HttpRequest httpRequest, boolean asJava) {
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

    public synchronized Expectation[] retrieve(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest key : requestResponseLog.keySet()) {
                for (HttpResponse value : requestResponseLog.getAll(key)) {
                    if (httpRequestMatcher.matches(key)) {
                        expectations.add(new Expectation(key, Times.once()).thenRespond(value));
                    }
                }
            }
        } else {
            for (HttpRequest key : requestResponseLog.keySet()) {
                for (HttpResponse value : requestResponseLog.getAll(key)) {
                    expectations.add(new Expectation(key, Times.once()).thenRespond(value));
                }
            }
        }
        return expectations.toArray(new Expectation[expectations.size()]);
    }

    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    public synchronized String verify(Verification verification) {
        if (verification != null) {
            List<HttpRequest> matchingRequests = new ArrayList<HttpRequest>();
            if (verification.getHttpRequest() != null) {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verification.getHttpRequest());
                for (HttpRequest httpRequest : requestLog) {
                    if (httpRequestMatcher.matches(httpRequest)) {
                        matchingRequests.add(httpRequest);
                    }
                }
            }

            HttpRequest[] allRequestsArray = requestLog.toArray(new HttpRequest[requestLog.size()]);
            if (verification.getTimes().getCount() != 0 && matchingRequests.isEmpty()) {
                return "Request not found " + verification.getTimes() + " expected:<" + httpRequestSerializer.serialize(verification.getHttpRequest()) + "> but was:<" + (allRequestsArray.length == 1 ? httpRequestSerializer.serialize(allRequestsArray[0]) : httpRequestSerializer.serialize(allRequestsArray)) + ">";
            }
            if (verification.getTimes().isExact()) {
                if (matchingRequests.size() != verification.getTimes().getCount()) {
                    return "Request not found " + verification.getTimes() + " expected:<" + httpRequestSerializer.serialize(verification.getHttpRequest()) + "> but was:<" + (allRequestsArray.length == 1 ? httpRequestSerializer.serialize(allRequestsArray[0]) : httpRequestSerializer.serialize(allRequestsArray)) + ">";
                }
            } else {
                if (matchingRequests.size() < verification.getTimes().getCount()) {
                    return "Request not found " + verification.getTimes() + " expected:<" + httpRequestSerializer.serialize(verification.getHttpRequest()) + "> but was:<" + (allRequestsArray.length == 1 ? httpRequestSerializer.serialize(allRequestsArray[0]) : httpRequestSerializer.serialize(allRequestsArray)) + ">";
                }
            }
        }

        return "";
    }
}
