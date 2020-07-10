package org.mockserver.matchers;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.RequestDefinition;

import java.util.Objects;

import static org.mockserver.character.Character.NEW_LINE;

public abstract class AbstractHttpRequestMatcher extends NotMatcher<RequestDefinition> implements HttpRequestMatcher {

    protected static final String REQUEST_DID_NOT_MATCH = "request:{}didn't match";
    protected static final String REQUEST_MATCHER = " request matcher";
    protected static final String EXPECTATION = " expectation";
    protected static final String BECAUSE = ":{}because:{}";
    protected static final String REQUEST_DID_MATCH = "request:{}matched request:{}";
    protected static final String EXPECTATION_DID_MATCH = "request:{}matched" + EXPECTATION + ":{}";
    protected static final String DID_NOT_MATCH = " didn't match";
    protected static final String MATCHED = " matched";
    protected static final String COLON_NEW_LINES = ": " + NEW_LINE + NEW_LINE;

    protected final MockServerLogger mockServerLogger;
    private int hashCode;
    private boolean isBlank = false;
    private boolean responseInProgress = false;
    protected boolean controlPlaneMatcher;
    protected Expectation expectation;
    protected String didNotMatchRequestBecause = REQUEST_DID_NOT_MATCH + REQUEST_MATCHER + BECAUSE;
    protected String didNotMatchExpectationBecause = REQUEST_DID_NOT_MATCH + EXPECTATION + BECAUSE;
    protected String didNotMatchExpectationWithoutBecause = REQUEST_DID_NOT_MATCH + EXPECTATION;

    protected AbstractHttpRequestMatcher(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public void setDescription(String description) {
        didNotMatchRequestBecause = REQUEST_DID_NOT_MATCH + description + REQUEST_MATCHER + BECAUSE;
        didNotMatchExpectationBecause = REQUEST_DID_NOT_MATCH + description + EXPECTATION + BECAUSE;
        didNotMatchExpectationWithoutBecause = REQUEST_DID_NOT_MATCH + description + EXPECTATION;
    }

    @Override
    public boolean update(Expectation expectation) {
        if (this.expectation != null && this.expectation.equals(expectation)) {
            return false;
        } else {
            this.controlPlaneMatcher = false;
            this.expectation = expectation;
            this.hashCode = 0;
            this.isBlank = expectation.getHttpRequest() == null;
            apply(expectation.getHttpRequest());
            return true;
        }
    }


    @Override
    public boolean update(RequestDefinition requestDefinition) {
        this.controlPlaneMatcher = true;
        this.expectation = null;
        this.hashCode = 0;
        this.isBlank = requestDefinition == null;
        return apply(requestDefinition);
    }

    public void setControlPlaneMatcher(boolean controlPlaneMatcher) {
        this.controlPlaneMatcher = controlPlaneMatcher;
    }

    abstract boolean apply(RequestDefinition requestDefinition);

    @Override
    public boolean matches(RequestDefinition requestDefinition) {
        return matches(null, requestDefinition);
    }

    @Override
    public abstract boolean matches(MatchDifference matchDifference, RequestDefinition requestDefinition);

    @Override
    public Expectation getExpectation() {
        return expectation;
    }

    public boolean isResponseInProgress() {
        return responseInProgress;
    }

    public HttpRequestMatcher setResponseInProgress(boolean responseInProgress) {
        this.responseInProgress = responseInProgress;
        return this;
    }

    public boolean isBlank() {
        return isBlank;
    }

    @Override
    public boolean isActive() {
        return expectation == null || expectation.isActive();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        HttpRequestPropertiesMatcher that = (HttpRequestPropertiesMatcher) o;
        return Objects.equals(expectation, that.expectation);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(expectation);
        }
        return hashCode;
    }
}
