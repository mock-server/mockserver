package org.mockserver.mock;

import org.mockserver.collections.CircularLinkedList;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.Action;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;
import static org.mockserver.metrics.Metrics.Name.*;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends MockServerMatcherNotifier {

    protected final List<HttpRequestMatcher> httpRequestMatchers = Collections.synchronizedList(new CircularLinkedList<HttpRequestMatcher>(maxExpectations()));
    private MatcherBuilder matcherBuilder;

    MockServerMatcher(MockServerLogger logFormatter, Scheduler scheduler) {
        super(scheduler);
        this.matcherBuilder = new MatcherBuilder(logFormatter);
    }

    public synchronized void add(Expectation expectation) {
        httpRequestMatchers.add(matcherBuilder.transformsToMatcher(expectation));
        notifyListeners(this);
        if (expectation != null && expectation.getAction() != null) {
            Metrics.increment(expectation.getAction().getType());
        }
    }

    private synchronized List<HttpRequestMatcher> cloneMatchers() {
        return new ArrayList<>(httpRequestMatchers);
    }

    public synchronized void reset() {
        httpRequestMatchers.clear();
        Metrics.clearActionMetrics();
        notifyListeners(this);
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Expectation matchingExpectation = null;
        for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
            if (httpRequestMatcher.matches(httpRequest, httpRequest)) {
                matchingExpectation = httpRequestMatcher.decrementRemainingMatches();
            }
            if (!httpRequestMatcher.isActive()) {
                if (httpRequestMatchers.contains(httpRequestMatcher)) {
                    httpRequestMatchers.remove(httpRequestMatcher);
                    Metrics.decrement(httpRequestMatcher.getExpectation().getAction().getType());
                    notifyListeners(this);
                }
            }
            if (matchingExpectation != null) {
                break;
            }
        }
        if (matchingExpectation == null || matchingExpectation.getAction() == null) {
            Metrics.increment(EXPECTATION_NOT_MATCHED_COUNT);
        } else if (matchingExpectation.getAction().getType().direction == Action.Direction.FORWARD) {
            Metrics.increment(FORWARD_EXPECTATION_MATCHED_COUNT);
        } else {
            Metrics.increment(RESPONSE_EXPECTATION_MATCHED_COUNT);
        }
        return matchingExpectation;
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher clearHttpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
                if (clearHttpRequestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                    if (httpRequestMatchers.contains(httpRequestMatcher)) {
                        httpRequestMatchers.remove(httpRequestMatcher);
                        Metrics.decrement(httpRequestMatcher.getExpectation().getAction().getType());
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
        HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
            if (httpRequest == null ||
                requestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                expectations.add(httpRequestMatcher.getExpectation());
            }
        }
        return expectations;
    }

    public boolean isEmpty() {
        return httpRequestMatchers.isEmpty();
    }
}
