package org.mockserver.client;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.closurecallback.websocketclient.WebSocketClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

@SuppressWarnings({"unused", "rawtypes"})
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
        when(mockAbstractClient.upsert(mockExpectation)).thenReturn(new Expectation[]{mockExpectation});
        forwardChainExpectation = new ForwardChainExpectation(new MockServerLogger(), new MockServerEventBus(), mockAbstractClient, mockExpectation);
        openMocks(this);
    }

    @Test
    public void shouldSetResponse() {
        // given
        HttpResponse response = response();

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.respond(response);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenRespond(same(response));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetResponseTemplate() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "some_template");

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.respond(template);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenRespond(same(template));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetResponseClassCallback() {
        // given
        HttpClassCallback callback = callback();

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.respond(callback);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenRespond(same(callback));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetForward() {
        // given
        HttpForward forward = forward();

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.forward(forward);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenForward(same(forward));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetForwardTemplate() {
        // given
        HttpTemplate template = template(HttpTemplate.TemplateType.VELOCITY, "some_template");

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.forward(template);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenForward(same(template));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetForwardClassCallback() {
        // given
        HttpClassCallback callback = callback();

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.forward(callback);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenForward(same(callback));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetOverrideForwardedRequest() {
        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_replaced_body")));

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenForward(forwardOverriddenRequest(request().withBody("some_replaced_body")));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

    @Test
    public void shouldSetError() {
        // given
        HttpError error = error();

        // when
        Expectation[] upsertedExpectations = forwardChainExpectation.error(error);

        // then
        assertThat(upsertedExpectations, is(new Expectation[]{mockExpectation}));
        verify(mockExpectation).thenError(same(error));
        verify(mockAbstractClient).upsert(mockExpectation);
    }

}
