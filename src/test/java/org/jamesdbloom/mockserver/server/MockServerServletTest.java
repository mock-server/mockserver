package org.jamesdbloom.mockserver.server;

import org.jamesdbloom.mockserver.client.serialization.ExpectationSerializer;
import org.jamesdbloom.mockserver.mappers.HttpServletRequestMapper;
import org.jamesdbloom.mockserver.mappers.HttpServletResponseMapper;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.mock.MockServer;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    @Mock
    private MockServer mockServer;
    @Mock
    private HttpServletRequestMapper httpServletRequestMapper;
    @Mock
    private HttpServletResponseMapper httpServletResponseMapper;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @InjectMocks
    private MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);
    }

    @Test
    public void respondWhenPathMatches() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("somepath");
        HttpResponse httpResponse = new HttpResponse().withHeaders(new Header("name", "value")).withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockServer.handle(httpRequest)).thenReturn(httpResponse);
        when(httpServletRequestMapper.createHttpRequest(httpServletRequest)).thenReturn(httpRequest);

        // when
        mockServerServlet.doGet(httpServletRequest, httpServletResponse);

        // then
        verify(httpServletResponseMapper).mapHttpServletResponse(httpResponse, httpServletResponse);
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        Expectation expectation = mock(Expectation.class);

        when(expectationSerializer.deserialize(httpServletRequest.getInputStream())).thenReturn(expectation);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServer).addExpectation(same(expectation));
    }

}
