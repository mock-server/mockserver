package org.mockserver.matchers;

import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;

import java.util.List;

public interface HttpRequestMatcher extends Matcher<RequestDefinition> {

    List<HttpRequest> getHttpRequests();

    boolean matches(final RequestDefinition request);

    boolean matches(MatchDifference context, RequestDefinition httpRequest);

    Expectation getExpectation();

    boolean update(Expectation expectation);

    boolean update(RequestDefinition requestDefinition);

    @SuppressWarnings("UnusedReturnValue")
    HttpRequestMatcher setResponseInProgress(boolean responseInProgress);

    boolean isResponseInProgress();

    boolean isActive();

}
