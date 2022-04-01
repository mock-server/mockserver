package org.mockserver.log.model;

import org.mockserver.model.ExpectationId;
import org.mockserver.model.RequestDefinition;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class RequestAndExpectationId {

    public final RequestDefinition requestDefinition;
    public final String expectationId;

    public RequestAndExpectationId(RequestDefinition requestDefinition, String expectationId) {
        this.requestDefinition = requestDefinition;
        this.expectationId = expectationId;
    }

    public RequestDefinition getRequestDefinition() {
        return requestDefinition;
    }

    public String getExpectationId() {
        return expectationId;
    }

    public boolean matches(ExpectationId expectationId) {
        if (expectationId != null && isNotBlank(expectationId.getId()) && isNotBlank(this.expectationId)) {
            return this.expectationId.equals(expectationId.getId());
        }
        return false;
    }
}
