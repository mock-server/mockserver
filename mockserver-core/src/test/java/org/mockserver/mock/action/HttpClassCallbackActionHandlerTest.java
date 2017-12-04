package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackActionHandlerTest {

    @Test
    public void shouldHandleHttpRequests() {
        // given
        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.HttpClassCallbackActionHandlerTest$TestCallback");

        // when
        HttpResponse actualHttpResponse = new HttpClassCallbackActionHandler().handle(httpClassCallback, request().withBody("some_body"));

        // then
        assertThat(actualHttpResponse, is(response("some_body")));
    }

    public static class TestCallback implements ExpectationCallback {

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return response(httpRequest.getBodyAsString());
        }
    }
}