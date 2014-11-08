package org.mockserver.filters;

import org.mockserver.collections.CircularMultiMap;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class Filters {

    private final CircularMultiMap<HttpRequestMatcher, ResponseFilter> responseFilters = new CircularMultiMap<HttpRequestMatcher, ResponseFilter>(100, 100);
    private final CircularMultiMap<HttpRequestMatcher, RequestFilter> requestFilters = new CircularMultiMap<HttpRequestMatcher, RequestFilter>(100, 100);
    private final MatcherBuilder matcherBuilder = new MatcherBuilder();

    public Filters withFilter(HttpRequest httpRequest, Filter filter) {
        if (filter instanceof RequestFilter) {
            requestFilters.put(matcherBuilder.transformsToMatcher(httpRequest), (RequestFilter) filter);
        }
        if (filter instanceof ResponseFilter) {
            responseFilters.put(matcherBuilder.transformsToMatcher(httpRequest), (ResponseFilter) filter);
        }
        return this;
    }

    public HttpRequest applyOnRequestFilters(HttpRequest httpRequest) {
        for (HttpRequestMatcher httpRequestMatcher : requestFilters.keySet()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                for (RequestFilter requestFilter : requestFilters.getAll(httpRequestMatcher)) {
                    if (httpRequest != null) {
                        httpRequest = requestFilter.onRequest(httpRequest);
                    }
                }
            }
        }
        return httpRequest;
    }

    public HttpResponse applyOnResponseFilters(HttpRequest httpRequest, HttpResponse httpResponse) {
        if (httpResponse != null) {
            for (HttpRequestMatcher httpRequestMatcher : responseFilters.keySet()) {
                if (httpRequestMatcher.matches(httpRequest)) {
                    for (ResponseFilter proxyFilter : responseFilters.getAll(httpRequestMatcher)) {
                        httpResponse = proxyFilter.onResponse(httpRequest, httpResponse);
                        if (httpResponse == null) {
                            throw new IllegalStateException(proxyFilter.getClass().getName() + " returned a null HttpResponse, Filters are not allowed to return a null HttpResponse object, a Filter can only return null for an HttpRequest which will prevent the request being sent.");
                        }
                    }
                }
            }
        }
        return httpResponse;
    }
}
