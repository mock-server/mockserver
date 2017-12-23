package org.mockserver.client;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;

import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpClassCallback.callback;

public class ForwardChainExpectationTest {

    private AbstractClient mockAbstractClient;

    private Expectation mockExpectation;

    @Mock
    private WebSocketClient webSocketClient;

    @InjectMocks
    private ForwardChainExpectation forwardChainExpectation;

    @Before
    public void setupMocks() {
        mockAbstractClient = mock(AbstractClient.class);
        mockExpectation = mock(Expectation.class);
        forwardChainExpectation = new ForwardChainExpectation(mockAbstractClient, mockExpectation);
        initMocks(this);
    }

    @Test
    public void shouldSetResponse() {
        // given
        HttpResponse response = response();

        // when
        forwardChainExpectation.respond(response);

        // then
        verify(mockExpectation).thenRespond(same(response));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetForward() {
        // given
        HttpForward forward = forward();

        // when
        forwardChainExpectation.forward(forward);

        // then
        verify(mockExpectation).thenForward(same(forward));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetError() {
        // given
        HttpError error = error();

        // when
        forwardChainExpectation.error(error);

        // then
        verify(mockExpectation).thenError(same(error));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetClassCallback() {
        // given
        HttpClassCallback callback = callback();

        // when
        forwardChainExpectation.callback(callback);

        // then
        verify(mockExpectation).thenCallback(same(callback));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetObjectCallback() {
        // given
        ExpectationCallback callback = new ExpectationCallback() {
            @Override
            public HttpResponse handle(HttpRequest httpRequest) {
                return response();
            }
        };

        // and
        when(webSocketClient.registerExpectationCallback(callback)).thenReturn(webSocketClient);
        when(webSocketClient.clientId()).thenReturn("some_client_id");

        // when
        forwardChainExpectation.callback(callback);

        // then
        verify(webSocketClient).registerExpectationCallback(same(callback));
        verify(mockExpectation).thenCallback(new HttpObjectCallback().withClientId("some_client_id"));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

}
