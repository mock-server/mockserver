package org.mockserver.matchers;

import org.mockserver.mock.Expectation;
import org.mockserver.model.RequestDefinition;

import java.util.Comparator;

public interface HttpRequestMatcher extends Matcher<RequestDefinition> {

    Comparator<? super HttpRequestMatcher> EXPECTATION_PRIORITY_COMPARATOR = Comparator.comparing(HttpRequestMatcher::getExpectation, Expectation.EXPECTATION_PRIORITY_COMPARATOR);

    boolean matches(final RequestDefinition request);

    boolean matches(MatchDifference matchDifference, RequestDefinition httpRequest);

    Expectation getExpectation();

    boolean update(Expectation expectation);

    boolean update(RequestDefinition requestDefinition);

    @SuppressWarnings("UnusedReturnValue")
    HttpRequestMatcher setResponseInProgress(boolean responseInProgress);

    boolean isResponseInProgress();

    boolean isActive();

}
