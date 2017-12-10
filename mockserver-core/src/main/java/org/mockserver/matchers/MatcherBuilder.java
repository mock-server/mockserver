package org.mockserver.matchers;

import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    private final LoggingFormatter logFormatter;

    public MatcherBuilder(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
    }

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher(httpRequest, logFormatter);
    }

    public HttpRequestMatcher transformsToMatcher(Expectation expectation) {
        return new HttpRequestMatcher(expectation, logFormatter);
    }

}
