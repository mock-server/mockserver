package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.callback.WebSocketRequestCallback;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class HttpForwardObjectCallbackActionHandlerTest {

    @Test
    public void shouldHandleHttpRequests() {
        // given
        WebSocketClientRegistry mockWebSocketClientRegistry = mock(WebSocketClientRegistry.class);
        HttpStateHandler mockHttpStateHandler = mock(HttpStateHandler.class);
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback().withClientId("some_clientId");
        HttpRequest request = request().withBody("some_body");
        ResponseWriter mockResponseWriter = mock(ResponseWriter.class);
        when(mockHttpStateHandler.getWebSocketClientRegistry()).thenReturn(mockWebSocketClientRegistry);

        // when
        new HttpForwardObjectCallbackActionHandler(mockHttpStateHandler, null).handle(mock(ActionHandler.class), httpObjectCallback, request, mockResponseWriter, true);

        // then
        verify(mockWebSocketClientRegistry).registerForwardCallbackHandler(any(String.class), any(WebSocketRequestCallback.class));
        verify(mockWebSocketClientRegistry).sendClientMessage(eq("some_clientId"), any(HttpRequest.class));
    }
}
