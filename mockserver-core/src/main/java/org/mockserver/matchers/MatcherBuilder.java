package org.mockserver.matchers;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    private final MockServerLogger logFormatter;

    public MatcherBuilder(MockServerLogger logFormatter) {
        this.logFormatter = logFormatter;
    }

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher(httpRequest, logFormatter);
    }

    public HttpRequestMatcher transformsToMatcher(Expectation expectation) {
        return new HttpRequestMatcher(expectation, logFormatter);
    }

}
