package org.mockserver.proxy.filters;

import org.mockserver.collections.CircularMultiMap;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class LogFilter implements ProxyResponseFilter {

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
}
