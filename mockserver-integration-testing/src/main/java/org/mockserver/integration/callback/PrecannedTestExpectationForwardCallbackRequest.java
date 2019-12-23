package org.mockserver.integration.callback;

import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.HttpRequest;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class PrecannedTestExpectationForwardCallbackRequest implements ExpectationForwardCallback {

    @Override
    public HttpRequest handle(HttpRequest httpRequest) {
        return request()
            .withHeader("Host", "localhost:" + httpRequest.getFirstHeader("x-echo-server-port"))
            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
            .withBody("some_overridden_body")
            .withSecure(httpRequest.isSecure());
    }

}
