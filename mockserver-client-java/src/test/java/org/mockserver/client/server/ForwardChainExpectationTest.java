package org.mockserver.client.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpResponse;

import static org.mockito.Matchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpCallback.callback;

public class ForwardChainExpectationTest {

    @Mock
    private MockServerClient mockMockServerClient;

    @Mock
    private Expectation mockExpectation;

    @Before
    public void setupMocks() {
        initMocks(this);
    }

    @Test
    public void shouldSetResponse() {
        // given
        ForwardChainExpectation forwardChainExpectation = new ForwardChainExpectation(mockMockServerClient, mockExpectation);

        // and
        HttpResponse response = response();

        // when
        forwardChainExpectation.respond(response);

        // then
        verify(mockExpectation).thenRespond(same(response));
        verify(mockMockServerClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetForward() {
        // given
        ForwardChainExpectation forwardChainExpectation = new ForwardChainExpectation(mockMockServerClient, mockExpectation);

        // and
        HttpForward forward = forward();

        // when
        forwardChainExpectation.forward(forward);

        // then
        verify(mockExpectation).thenForward(same(forward));
        verify(mockMockServerClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetError() {
        // given
        ForwardChainExpectation forwardChainExpectation = new ForwardChainExpectation(mockMockServerClient, mockExpectation);

        // and
        HttpError error = error();

        // when
        forwardChainExpectation.error(error);

        // then
        verify(mockExpectation).thenError(same(error));
        verify(mockMockServerClient).sendExpectation(mockExpectation);
    }

    @Test
    public void shouldSetCallback() {
        // given
        ForwardChainExpectation forwardChainExpectation = new ForwardChainExpectation(mockMockServerClient, mockExpectation);

        // and
        HttpCallback callback = callback();

        // when
        forwardChainExpectation.callback(callback);

        // then
        verify(mockExpectation).thenCallback(same(callback));
        verify(mockMockServerClient).sendExpectation(mockExpectation);
    }

}