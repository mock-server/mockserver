package org.mockserver.netty.integration.mock;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class TestClasspathTestExpectationResponseCallback implements ExpectationResponseCallback {

    public static final List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
    public static HttpResponse httpResponse = response();

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        httpRequests.add(httpRequest);
        return httpResponse;
    }
}
