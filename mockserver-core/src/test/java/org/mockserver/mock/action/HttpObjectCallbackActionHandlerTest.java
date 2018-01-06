package org.mockserver.mock.action;

import org.junit.Test;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.callback.ExpectationCallbackResponse;
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
public class HttpObjectCallbackActionHandlerTest {

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
        new HttpObjectCallbackActionHandler(mockHttpStateHandler).handle(httpObjectCallback, request, mockResponseWriter);

        // then
        verify(mockWebSocketClientRegistry).registerCallbackResponseHandler(eq("some_clientId"), any(ExpectationCallbackResponse.class));
        verify(mockWebSocketClientRegistry).sendClientMessage("some_clientId", request);
    }
}
