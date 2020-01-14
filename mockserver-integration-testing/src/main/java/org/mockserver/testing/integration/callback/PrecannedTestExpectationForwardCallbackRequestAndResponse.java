package org.mockserver.testing.integration.callback;

import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class PrecannedTestExpectationForwardCallbackRequestAndResponse implements ExpectationForwardAndResponseCallback {

    @Override
    public HttpRequest handle(HttpRequest httpRequest) {
        return request()
            .withHeader("Host", "localhost:" + httpRequest.getFirstHeader("x-echo-server-port"))
            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
            .withBody("some_overridden_body")
            .withSecure(httpRequest.isSecure());
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) {
        HttpResponse response = response()
            .withHeader("x-response-test", "x-response-test")
            .withBody("some_overidden_response_body");
        for (Header header : httpResponse.getHeaderList()) {
            if (!header.getName().equalsIgnoreCase("Content-Length")) {
                response.withHeader(header);
            }
        }
        return response;
    }

}
