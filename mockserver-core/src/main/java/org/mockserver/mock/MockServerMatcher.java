package org.mockserver.mock;

import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.collections.CircularLinkedList;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.Action;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;
import static org.mockserver.metrics.Metrics.Name.*;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends MockServerMatcherNotifier {

    final List<HttpRequestMatcher> httpRequestMatchers = Collections.synchronizedList(new CircularLinkedList<>(maxExpectations()));
    private WebSocketClientRegistry webSocketClientRegistry;
    private MatcherBuilder matcherBuilder;

    MockServerMatcher(MockServerLogger logFormatter, Scheduler scheduler, WebSocketClientRegistry webSocketClientRegistry) {
        super(scheduler);
        this.matcherBuilder = new MatcherBuilder(logFormatter);
        this.webSocketClientRegistry = webSocketClientRegistry;
    }

    public void add(Expectation expectation) {
        httpRequestMatchers.add(matcherBuilder.transformsToMatcher(expectation));
        notifyListeners(this);
        if (expectation != null && expectation.getAction() != null) {
            Metrics.increment(expectation.getAction().getType());
        }
    }

    private HttpRequestMatcher[] cloneMatchers() {
        return httpRequestMatchers.toArray(new HttpRequestMatcher[0]);
    }

    public void reset() {
        httpRequestMatchers.clear();
        Metrics.clearActionMetrics();
        notifyListeners(this);
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Expectation matchingExpectation = null;
        for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
            if (httpRequestMatcher.matches(httpRequest, httpRequest)) {
                matchingExpectation = httpRequestMatcher.getExpectation();
                if (matchingExpectation.decrementRemainingMatches()) {
                    notifyListeners(this);
                }
            }
            if (!httpRequestMatcher.isActive()) {
                removeHttpRequestMatcher(httpRequestMatcher);
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
                    removeHttpRequestMatcher(httpRequestMatcher);
                }
            }
        } else {
            reset();
        }
    }

    private void removeHttpRequestMatcher(HttpRequestMatcher httpRequestMatcher) {
        if (httpRequestMatchers.contains(httpRequestMatcher)) {
            httpRequestMatchers.remove(httpRequestMatcher);
            if (httpRequestMatcher.getExpectation() != null) {
                final Action action = httpRequestMatcher.getExpectation().getAction();
                if (action != null) {
                    switch (action.getType()) {
                        case FORWARD_OBJECT_CALLBACK:
                            webSocketClientRegistry.unregisterForwardCallbackHandler(((HttpObjectCallback) action).getClientId());
                            break;
                        case RESPONSE_OBJECT_CALLBACK:
                            webSocketClientRegistry.unregisterResponseCallbackHandler(((HttpObjectCallback) action).getClientId());
                            break;
                    }
                    Metrics.decrement(action.getType());
                }
            }
            notifyListeners(this);
        }
    }

    public List<Expectation> retrieveActiveExpectations(HttpRequest httpRequest) {
        if (httpRequest == null) {
            return httpRequestMatchers.stream().map(HttpRequestMatcher::getExpectation).collect(Collectors.toList());
        } else {
            List<Expectation> expectations = new ArrayList<>();
            HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequestMatcher httpRequestMatcher : cloneMatchers()) {
                if (requestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                    expectations.add(httpRequestMatcher.getExpectation());
                }
            }
            return expectations;
        }
    }

    public boolean isEmpty() {
        return httpRequestMatchers.isEmpty();
    }
}
