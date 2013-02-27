package org.jamesdbloom.mockserver.client;

import org.jamesdbloom.mockserver.client.serialization.model.ExpectationDTO;
import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Mock
    private ExpectationMapper expectationMapper;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() {
        mockServerClient = spy(new MockServerClient("localhost", 8080));
        doNothing().when(mockServerClient).sendExpectation(any(Expectation.class));

        initMocks(this);
    }

    @Test
    public void setupExpectation() {
        // given
        HttpRequest httpRequest = new HttpRequest().withBody("some_request_body").withHeaders(new Header("requestName", "requestValue"));
        HttpResponse httpResponse = new HttpResponse().withBody("some_response_body").withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }
}
