package org.jamesdbloom.mockserver.client;

import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class ExpectationDTO extends ModelObject {

    private final HttpRequestMatcher httpRequestMatcher;
    private final MockServerClient mockServerClient;
    private final Times times;
    private HttpResponse httpResponse;

    public ExpectationDTO(MockServerClient mockServerClient, HttpRequestMatcher httpRequestMatcher, Times times) {
        this.mockServerClient = mockServerClient;
        this.httpRequestMatcher = httpRequestMatcher;
        this.times = times;
    }

    public MockServerClient respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        mockServerClient.sendExpectation(this);
        return mockServerClient;
    }

    public HttpRequestMatcher getHttpRequestMatcher() {
        return httpRequestMatcher;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public Times getTimes() {
        return times;
    }
}
