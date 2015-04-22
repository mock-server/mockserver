package org.mockserver.mock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jamesdbloom
 */
public class MockServerMatcher extends ObjectWithReflectiveEqualsHashCodeToString {

    protected final List<Expectation> expectations = Collections.synchronizedList(new ArrayList<Expectation>());
    private Logger requestLogger = LoggerFactory.getLogger("REQUEST");

    public Expectation when(HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public Expectation when(final HttpRequest httpRequest, Times times) {
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

    public Action handle(HttpRequest httpRequest) {
        ArrayList<Expectation> expectations = new ArrayList<Expectation>(this.expectations);
        for (Expectation expectation : expectations) {
            if (expectation.matches(httpRequest)) {
                expectation.decrementRemainingMatches();
                if (!expectation.getTimes().greaterThenZero()) {
                    if (this.expectations.contains(expectation)) {
                        this.expectations.remove(expectation);
                    }
                }
                return expectation.getAction(true);
            }
        }
        return null;
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = new MatcherBuilder().transformsToMatcher(httpRequest);
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
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

    public void dumpToLog(HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationSerializer expectationSerializer = new ExpectationSerializer();
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
                if (expectation.matches(httpRequest)) {
                    requestLogger.warn(cleanBase64Response(expectationSerializer.serialize(expectation)));
                }
            }
        } else {
            ExpectationSerializer expectationSerializer = new ExpectationSerializer();
            for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
                requestLogger.warn(cleanBase64Response(expectationSerializer.serialize(expectation)));
            }
        }
    }

    @VisibleForTesting
    String cleanBase64Response(String serializedExpectation) {
        Pattern base64ResponseBodyPattern = Pattern.compile("[\\s\\S]*\\\"httpResponse\\\"\\s*\\:\\s*\\{[\\s\\S]*\\\"body\\\"\\s*\\:\\s*\\\"(([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==))\\\"[\\s\\S]*");
        Matcher matcher = base64ResponseBodyPattern.matcher(serializedExpectation);
        if (matcher.find()) {
            return serializedExpectation.replace(matcher.group(1), new String(Base64Converter.base64StringToBytes(matcher.group(1))));
        } else {
            return serializedExpectation;
        }
    }
}
