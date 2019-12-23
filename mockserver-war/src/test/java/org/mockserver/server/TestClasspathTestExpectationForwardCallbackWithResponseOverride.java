package org.mockserver.server;

import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class TestClasspathTestExpectationForwardCallbackWithResponseOverride implements ExpectationForwardAndResponseCallback {

    public static final List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
    public static final List<HttpResponse> httpResponses = new ArrayList<>();
    public static HttpRequest httpRequestToReturn = request();
    public static HttpResponse httpResponseToReturn = response();

    @Override
    public HttpRequest handle(HttpRequest httpRequest) {
        httpRequests.add(httpRequest);
        return httpRequestToReturn;
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) {
        httpResponses.add(httpResponse);
        return httpResponseToReturn;
    }
}
