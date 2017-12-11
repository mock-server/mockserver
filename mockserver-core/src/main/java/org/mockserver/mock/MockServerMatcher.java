package org.mockserver.mock;

import org.mockserver.collections.CircularLinkedList;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends ObjectWithReflectiveEqualsHashCodeToString {

    protected final List<HttpRequestMatcher> httpRequestMatchers = Collections.synchronizedList(new CircularLinkedList<HttpRequestMatcher>(maxExpectations()));
    private MatcherBuilder matcherBuilder;

    public MockServerMatcher(LoggingFormatter logFormatter) {
        this.matcherBuilder = new MatcherBuilder(logFormatter);
    }

    public void add(Expectation expectation) {
        this.httpRequestMatchers.add(matcherBuilder.transformsToMatcher(expectation));
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Expectation matchingExpectation = null;
        for (HttpRequestMatcher httpRequestMatcher : new ArrayList<>(this.httpRequestMatchers)) {
            if (httpRequestMatcher.matches(httpRequest)) {
                matchingExpectation = httpRequestMatcher.decrementRemainingMatches();
            }
            if (!httpRequestMatcher.isActive()) {
                if (this.httpRequestMatchers.contains(httpRequestMatcher)) {
                    this.httpRequestMatchers.remove(httpRequestMatcher);
                }
            }
            if (matchingExpectation != null) {
                break;
            }
        }
        return matchingExpectation;
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher clearHttpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequestMatcher httpRequestMatcher : new ArrayList<>(this.httpRequestMatchers)) {
                if (clearHttpRequestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest(), false)) {
                    if (this.httpRequestMatchers.contains(httpRequestMatcher)) {
                        this.httpRequestMatchers.remove(httpRequestMatcher);
                    }
                }
            }
        } else {
            reset();
        }
    }

    public void reset() {
        this.httpRequestMatchers.clear();
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        for (HttpRequestMatcher httpRequestMatcher : new ArrayList<>(this.httpRequestMatchers)) {
            if (httpRequest == null || httpRequestMatcher.matches(httpRequest)) {
                expectations.add(httpRequestMatcher.getExpectation());
            }
        }
        return expectations;
    }
}
