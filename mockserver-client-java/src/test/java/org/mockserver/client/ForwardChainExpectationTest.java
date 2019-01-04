package org.mockserver.client;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.*;

import java.util.concurrent.Semaphore;

import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpTemplate.template;

public class ForwardChainExpectationTest {

    private MockServerClient mockAbstractClient;

    private Expectation mockExpectation;

    @Mock
    private WebSocketClient webSocketClient;

    @InjectMocks
    private ForwardChainExpectation forwardChainExpectation;

    @Before
    public void setupMocks() {
        mockAbstractClient = mock(MockServerClient.class);
        mockExpectation = mock(Expectation.class);
        Semaphore availableWebSocketCallbackRegistrations = new Semaphore(1);
        forwardChainExpectation = new ForwardChainExpectation(mockAbstractClient, mockExpectation, availableWebSocketCallbackRegistrations);
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
    public void shouldSetResponseTemplate() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "some_template");

        // when
        forwardChainExpectation.respond(template);

        // then
        verify(mockExpectation).thenRespond(same(template));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetResponseClassCallback() {
        // given
        HttpClassCallback callback = callback();

        // when
        forwardChainExpectation.respond(callback);

        // then
        verify(mockExpectation).thenRespond(same(callback));
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
    public void shouldSetForwardTemplate() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "some_template");

        // when
        forwardChainExpectation.forward(template);

        // then
        verify(mockExpectation).thenForward(same(template));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetForwardClassCallback() {
        // given
        HttpClassCallback callback = callback();

        // when
        forwardChainExpectation.forward(callback);

        // then
        verify(mockExpectation).thenForward(same(callback));
        verify(mockAbstractClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetOverrideForwardedRequest() {
        // when
        forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_replaced_body")));

        // then
        verify(mockExpectation).thenForward(forwardOverriddenRequest(request().withBody("some_replaced_body")));
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

}
