package org.mockserver.integration.callback;

import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class PrecannedTestExpectationCallback implements ExpectationCallback {

    public static HttpResponse httpResponse = new HttpResponse()
            .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
            .withHeaders(
                    new Header("x-callback", "test_callback_header")
            )
            .withBody("a_callback_response");

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        if (httpRequest.getPath().equals("/callback")) {
            return httpResponse;
        } else {
            return response().withStatusCode(404);
        }
    }
}
