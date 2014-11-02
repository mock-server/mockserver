package org.mockserver.server;

import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class TestClasspathTestExpectationCallback implements ExpectationCallback {

    public static List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
    public static HttpResponse httpResponse = response();

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        httpRequests.add(httpRequest);
        return httpResponse;
    }
}
