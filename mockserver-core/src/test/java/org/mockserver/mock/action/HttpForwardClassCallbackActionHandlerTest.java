package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpForwardClassCallbackActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpForwardClassCallbackActionHandler httpForwardClassCallbackActionHandler;

    @Before
    public void setupFixture() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger logFormatter = new MockServerLogger(HttpForwardClassCallbackActionHandlerTest.class);
        httpForwardClassCallbackActionHandler = new HttpForwardClassCallbackActionHandler(logFormatter, mockHttpClient);

        initMocks(this);
    }

    @Test
    public void shouldHandleInvalidClass() throws Exception {
        // given
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        httpResponse.set(notFoundResponse());

        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.FooBar");

        // when
        SettableFuture<HttpResponse> actualHttpRequest = httpForwardClassCallbackActionHandler.handle(httpClassCallback, request().withBody("some_body"));

        // then
        assertThat(actualHttpRequest.get(), is(notFoundResponse()));
        verifyZeroInteractions(mockHttpClient);
    }

    @Test
    public void shouldHandleValidLocalClass() throws Exception {
        // given
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        httpResponse.set(response("some_response_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), isNull(InetSocketAddress.class))).thenReturn(httpResponse);

        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.HttpForwardClassCallbackActionHandlerTest$TestCallback");

        // when
        SettableFuture<HttpResponse> actualHttpRequest = httpForwardClassCallbackActionHandler.handle(httpClassCallback, request().withBody("some_body"));

        // then
        assertThat(actualHttpRequest.get(), is(httpResponse.get()));
        verify(mockHttpClient).sendRequest(request("some_path"), null);
    }

    public static class TestCallback implements ExpectationForwardCallback {

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return request("some_path");
        }
    }
}
