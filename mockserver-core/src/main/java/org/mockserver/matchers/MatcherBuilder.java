package org.mockserver.matchers;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    private final MockServerLogger mockServerLogger;

    public MatcherBuilder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public HttpRequestMatcher transformsToMatcher(RequestDefinition requestDefinition) {
        HttpRequestMatcher httpRequestMatcher = null;
        if (requestDefinition instanceof OpenAPIDefinition) {
            httpRequestMatcher = new OpenAPIMatcher(mockServerLogger);
        } else {
            httpRequestMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        }
        httpRequestMatcher.update(requestDefinition);
        return httpRequestMatcher;
    }

    public HttpRequestMatcher transformsToMatcher(Expectation expectation) {
        HttpRequestMatcher httpRequestMatcher = null;
        if (expectation.getHttpRequest() instanceof OpenAPIDefinition) {
            httpRequestMatcher = new OpenAPIMatcher(mockServerLogger);
        } else {
            httpRequestMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        }
        httpRequestMatcher.update(expectation);
        return httpRequestMatcher;
    }

}
