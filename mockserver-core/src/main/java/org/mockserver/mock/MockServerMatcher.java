package org.mockserver.mock;

import org.mockserver.collections.CircularLinkedList;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.ui.MockServerMatcherNotifier;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends MockServerMatcherNotifier {

    protected final List<HttpRequestMatcher> httpRequestMatchers = Collections.synchronizedList(new CircularLinkedList<HttpRequestMatcher>(maxExpectations()));
    private MatcherBuilder matcherBuilder;

    MockServerMatcher(LoggingFormatter logFormatter) {
        this.matcherBuilder = new MatcherBuilder(logFormatter);
    }

    public synchronized void add(Expectation expectation) {
        this.httpRequestMatchers.add(matcherBuilder.transformsToMatcher(expectation));
        notifyListeners(this);
    }

    private synchronized List<HttpRequestMatcher> cloneMatchers() {
        return new ArrayList<>(this.httpRequestMatchers);
    }

    public synchronized void reset() {
        this.httpRequestMatchers.clear();
        notifyListeners(this);
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Expectation matchingExpectation = null;
        for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
            if (httpRequestMatcher.matches(httpRequest)) {
                matchingExpectation = httpRequestMatcher.decrementRemainingMatches();
            }
            if (!httpRequestMatcher.isActive()) {
                if (this.httpRequestMatchers.contains(httpRequestMatcher)) {
                    this.httpRequestMatchers.remove(httpRequestMatcher);
                    notifyListeners(this);
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
            for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
                if (clearHttpRequestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest(), false)) {
                    if (this.httpRequestMatchers.contains(httpRequestMatcher)) {
                        this.httpRequestMatchers.remove(httpRequestMatcher);
                        notifyListeners(this);
                    }
                }
            }
        } else {
            reset();
        }
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
            if (httpRequest == null || httpRequestMatcher.matches(httpRequest, false)) {
                expectations.add(httpRequestMatcher.getExpectation());
            }
        }
        return expectations;
    }
}
