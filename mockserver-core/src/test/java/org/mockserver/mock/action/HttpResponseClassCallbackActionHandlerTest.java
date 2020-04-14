package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpResponseClassCallbackActionHandlerTest {

    @Test
    public void shouldHandleInvalidClass() {
        // given
        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.FooBar");

        // when
        HttpResponse actualHttpResponse = new HttpResponseClassCallbackActionHandler(new MockServerLogger()).handle(httpClassCallback, request().withBody("some_body"));

        // then
        assertThat(actualHttpResponse, is(notFoundResponse()));
    }

    @Test
    public void shouldHandleValidLocalClass() {
        // given
        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.HttpResponseClassCallbackActionHandlerTest$TestCallback");

        // when
        HttpResponse actualHttpResponse = new HttpResponseClassCallbackActionHandler(new MockServerLogger()).handle(httpClassCallback, request().withBody("some_body"));

        // then
        assertThat(actualHttpResponse, is(response("some_body")));
    }

    @Test
    public void shouldHandleValidLocalClassViaType() {
        // given
        HttpClassCallback httpClassCallback = callback(TestCallback.class);

        // when
        HttpResponse actualHttpResponse = new HttpResponseClassCallbackActionHandler(new MockServerLogger()).handle(httpClassCallback, request().withBody("some_body"));

        // then        
        assertThat(actualHttpResponse, is(response("some_body")));
    }

    public static class TestCallback implements ExpectationResponseCallback {

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return response(httpRequest.getBodyAsString());
        }
    }
}
