package org.mockserver.integration.callback;

import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.HttpRequest;

import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class PrecannedTestExpectationForwardCallback implements ExpectationForwardCallback {

    @Override
    public HttpRequest handle(HttpRequest httpRequest) {
        return request()
            .withHeader("Host", "localhost:" + httpRequest.getFirstHeader("x-echo-server-port"))
            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
            .withBody("some_overridden_body")
            .withSecure(httpRequest.isSecure());
    }
}
