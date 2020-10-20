package org.mockserver.mock.action.http;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.closurecallback.websocketregistry.WebSocketRequestCallback;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpForwardObjectCallbackActionHandlerTest {

    @Test
    public void shouldHandleHttpRequests() {
        // given
        WebSocketClientRegistry mockWebSocketClientRegistry = mock(WebSocketClientRegistry.class);
        HttpState mockHttpStateHandler = mock(HttpState.class);
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_clientId");
        HttpRequest request = request().withBody("some_body");
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);
        when(mockHttpStateHandler.getWebSocketClientRegistry()).thenReturn(mockWebSocketClientRegistry);
        when(mockHttpStateHandler.getMockServerLogger()).thenReturn(new MockServerLogger());

        // when
        new HttpForwardObjectCallbackActionHandler(mockHttpStateHandler, null).handle(mock(HttpActionHandler.class), httpObjectCallback, request, mockResponseWriter, true, null);

        // then
        verify(mockWebSocketClientRegistry).registerForwardCallbackHandler(any(String.class), any(WebSocketRequestCallback.class));
        verify(mockWebSocketClientRegistry).sendClientMessage(eq("some_clientId"), any(HttpRequest.class), isNull());
    }

    @Test
    public void shouldReturnNotFound() throws ExecutionException, InterruptedException {
        // given
        HttpActionHandler mockActionHandler = mock(HttpActionHandler.class);
        HttpState mockHttpStateHandler = mock(HttpState.class);
        WebSocketClientRegistry mockWebSocketClientRegistry = mock(WebSocketClientRegistry.class);
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_clientId");
        HttpRequest request = request().withBody("some_body");
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);
        when(mockHttpStateHandler.getWebSocketClientRegistry()).thenReturn(mockWebSocketClientRegistry);
        when(mockHttpStateHandler.getMockServerLogger()).thenReturn(new MockServerLogger());
        when(mockWebSocketClientRegistry.sendClientMessage(eq("some_clientId"), any(HttpRequest.class), isNull())).thenReturn(false);

        // when
        new HttpForwardObjectCallbackActionHandler(mockHttpStateHandler, null).handle(mockActionHandler, httpObjectCallback, request, mockResponseWriter, true, null);

        // then
        verify(mockWebSocketClientRegistry).registerForwardCallbackHandler(any(String.class), any(WebSocketRequestCallback.class));
        verify(mockWebSocketClientRegistry).sendClientMessage(eq("some_clientId"), any(HttpRequest.class), isNull());
        ArgumentCaptor<HttpForwardActionResult> httpForwardActionResultArgumentCaptor = ArgumentCaptor.forClass(HttpForwardActionResult.class);
        verify(mockActionHandler).writeForwardActionResponse(httpForwardActionResultArgumentCaptor.capture(), same(mockResponseWriter), same(request), same(httpObjectCallback), eq(true));
        assertThat(httpForwardActionResultArgumentCaptor.getValue().getHttpResponse().get(), is(notFoundResponse()));
    }

}
