package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.callback.WebSocketResponseCallback;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class HttpResponseObjectCallbackActionHandlerTest {

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
        new HttpResponseObjectCallbackActionHandler(mockHttpStateHandler).handle(mock(ActionHandler.class), httpObjectCallback, request, mockResponseWriter, true);

        // then
        verify(mockWebSocketClientRegistry).registerResponseCallbackHandler(any(String.class), any(WebSocketResponseCallback.class));
        verify(mockWebSocketClientRegistry).sendClientMessage(eq("some_clientId"), any(HttpRequest.class));
    }
}
