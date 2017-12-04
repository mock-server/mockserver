package org.mockserver.mock;

import org.mockserver.collections.CircularLinkedList;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends ObjectWithReflectiveEqualsHashCodeToString {

    protected final List<Expectation> expectations = Collections.synchronizedList(new CircularLinkedList<Expectation>(maxExpectations()));

    public void add(Expectation expectation) {
        this.expectations.add(expectation);
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Expectation matchingExpectation = null;
        for (Expectation expectation : new ArrayList<>(this.expectations)) {
            if (expectation.matches(httpRequest)) {
                matchingExpectation = expectation.decrementRemainingMatches();
            }
            if (!expectation.isStillAlive() || !expectation.hasRemainingMatches()) {
                if (this.expectations.contains(expectation)) {
                    this.expectations.remove(expectation);
                }
                HttpObjectCallback httpObjectCallback = expectation.getHttpObjectCallback();
                if (httpObjectCallback != null) {

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
            HttpRequestMatcher httpRequestMatcher = new MatcherBuilder().transformsToMatcher(httpRequest);
            for (Expectation expectation : new ArrayList<>(this.expectations)) {
                if (httpRequestMatcher.matches(expectation.getHttpRequest(), true)) {
                    if (this.expectations.contains(expectation)) {
                        this.expectations.remove(expectation);
                    }
                }
            }
        } else {
            reset();
        }
    }

    public void reset() {
        this.expectations.clear();
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> expectations = new ArrayList<Expectation>();
        if (httpRequest != null) {
            for (Expectation expectation : new ArrayList<>(this.expectations)) {
                if (expectation.matches(httpRequest)) {
                    expectations.add(expectation);
                }
            }
        } else {
            expectations.addAll(this.expectations);
        }
        return expectations;
    }
}
