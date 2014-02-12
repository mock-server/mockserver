package org.mockserver.mock;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends EqualsHashCodeToString {

    protected final List<Expectation> expectations = new ArrayList<Expectation>();
    private Logger requestLogger = LoggerFactory.getLogger("REQUEST");

    public synchronized Expectation when(HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public synchronized Expectation when(final HttpRequest httpRequest, Times times) {
        Expectation expectation;
        if (times.isUnlimited()) {
            Collection<Expectation> existingExpectationsWithMatchingRequest = Collections2.filter(expectations, new Predicate<Expectation>() {
                public boolean apply(Expectation expectation) {
                    return expectation.contains(httpRequest);
                }
            });
            if (!existingExpectationsWithMatchingRequest.isEmpty()) {
                for (Expectation existingExpectation : existingExpectationsWithMatchingRequest) {
                    existingExpectation.setNotUnlimitedResponses();
                }
                expectation = new Expectation(httpRequest, Times.once());
            } else {
                expectation = new Expectation(httpRequest, Times.unlimited());
            }
        } else {
            expectation = new Expectation(httpRequest, times);
        }
        expectations.add(expectation);
        return expectation;
    }

    public synchronized HttpResponse handle(HttpRequest httpRequest) {
        ArrayList<Expectation> expectations = new ArrayList<Expectation>(this.expectations);
        for (Expectation expectation : expectations) {
            if (expectation.matches(httpRequest)) {
                if (!expectation.getTimes().greaterThenZero()) {
                    if (this.expectations.contains(expectation)) {
                        this.expectations.remove(expectation);
                    }
                }
                return expectation.getHttpResponse();
            }
        }
        return null;
    }

    public synchronized void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = new MatcherBuilder().transformsToMatcher(httpRequest);
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
                if (httpRequestMatcher.matches(expectation.getHttpRequest())) {
                    if (this.expectations.contains(expectation)) {
                        this.expectations.remove(expectation);
                    }
                }
            }
        } else {
            reset();
        }
    }

    public synchronized void reset() {
        this.expectations.clear();
    }

    public synchronized void dumpToLog(HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationSerializer expectationSerializer = new ExpectationSerializer();
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
                if (expectation.matches(httpRequest)) {
                    requestLogger.warn(expectationSerializer.serialize(expectation));
                }
            }
        } else {
            ExpectationSerializer expectationSerializer = new ExpectationSerializer();
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
                requestLogger.warn(expectationSerializer.serialize(expectation));
            }
        }
    }
}
