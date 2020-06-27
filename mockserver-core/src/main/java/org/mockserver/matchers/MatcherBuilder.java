package org.mockserver.matchers;

import org.mockserver.cache.LRUCache;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    private final MockServerLogger mockServerLogger;
    private final LRUCache<RequestDefinition, HttpRequestMatcher> requestMatcherLRUCache;

    public MatcherBuilder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.requestMatcherLRUCache = new LRUCache<>(mockServerLogger, 250, MINUTES.toMillis(10));
    }

    public HttpRequestMatcher transformsToMatcher(RequestDefinition requestDefinition) {
        HttpRequestMatcher httpRequestMatcher = requestMatcherLRUCache.get(requestDefinition);
        if (httpRequestMatcher == null) {
            if (requestDefinition instanceof OpenAPIDefinition) {
                httpRequestMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
            } else {
                httpRequestMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
            }
            httpRequestMatcher.update(requestDefinition);
            requestMatcherLRUCache.put(requestDefinition, httpRequestMatcher);
        }
        return httpRequestMatcher;
    }

    public HttpRequestMatcher transformsToMatcher(Expectation expectation) {
        HttpRequestMatcher httpRequestMatcher;
        if (expectation.getHttpRequest() instanceof OpenAPIDefinition) {
            httpRequestMatcher = new HttpRequestsPropertiesMatcher(mockServerLogger);
        } else {
            httpRequestMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        }
        httpRequestMatcher.update(expectation);
        return httpRequestMatcher;
    }

}
