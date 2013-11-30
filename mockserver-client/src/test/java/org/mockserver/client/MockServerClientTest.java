package org.mockserver.client;

import org.mockserver.mappers.ExpectationMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.*;
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
