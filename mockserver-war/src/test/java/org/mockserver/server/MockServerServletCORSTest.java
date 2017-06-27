package org.mockserver.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class MockServerServletCORSTest {

    @Mock
    private MockServerMatcher mockMockServerMatcher;
    @Mock
    private HttpServletRequestToMockServerRequestDecoder mockHttpServletRequestToMockServerRequestDecoder;
    @Mock
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @Mock
    private ActionHandler mockActionHandler;
    @Mock
    private RequestLogFilter mockRequestLogFilter;
    @InjectMocks
    private MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);
    }

    @Test
    public void shouldAddCORSHeadersForOptionsRequest() {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        assertThat(httpServletResponse.getStatus(), is(200));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldNotAddCORSHeadersForOptionsRequestWithoutOrigin() {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("OPTIONS");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then
        assertThat(httpServletResponse.getStatus(), is(200));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Methods"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), nullValue());
        assertThat(httpServletResponse.getHeader("X-CORS"), nullValue());
    }

    @Test
    public void shouldNotAddCORSHeadersForOptionsRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORSForAPI();
        try {
            // given - a request
            MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
            HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");
            when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

            // and - cors disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // when
            mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

            // then - correct response written to ChannelHandlerContext
            assertThat(httpServletResponse.getStatus(), is(200));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Expose-Methods"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), nullValue());
            assertThat(httpServletResponse.getHeader("X-CORS"), nullValue());
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersForOptionsRequestIfEnabledForAllRequestButDisabledForAPI() {
        boolean originalValueForAPI = ConfigurationProperties.enableCORSForAPI();
        boolean originalValueForAllRequests = ConfigurationProperties.enableCORSForAllResponses();
        try {
            // given - a request
            MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
            HttpRequest request = request().withMethod("OPTIONS").withHeader("Origin", "some_origin");
            when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

            // and - cors for API disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // but - cors for all request enabled
            ConfigurationProperties.enableCORSForAllResponses(true);

            // when
            mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

            // then - correct response written to ChannelHandlerContext
            assertThat(httpServletResponse.getStatus(), is(200));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
            assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValueForAPI);
            ConfigurationProperties.enableCORSForAllResponses(originalValueForAllRequests);
        }
    }

    @Test
    public void shouldAddCORSHeadersToRandomRequestIfDisabledForAllRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withPath("/randomPath");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getStatus(), is(200));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), nullValue());
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), nullValue());
        assertThat(httpServletResponse.getHeader("X-CORS"), nullValue());
    }

    @Test
    public void shouldAddCORSHeadersToRandomRequestIfEnabledForAllRequest() {
        boolean originalValue = ConfigurationProperties.enableCORSForAllResponses();
        try {
            // given - a request
            MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
            HttpRequest request = request().withPath("/randomPath");
            when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

            // and - cors disabled
            ConfigurationProperties.enableCORSForAllResponses(true);

            // when
            mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

            // then - correct response written to ChannelHandlerContext
            assertThat(httpServletResponse.getStatus(), is(200));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
            assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
            assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
        } finally {
            ConfigurationProperties.enableCORSForAllResponses(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersToStatusRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/status");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldNotAddCORSHeadersForStatusRequestIfCORSDisabled() {
        boolean originalValue = ConfigurationProperties.enableCORSForAPI();
        try {
            // given - a request
            MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
            HttpRequest request = request().withMethod("PUT").withPath("/status");
            when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

            // and - cors disabled
            ConfigurationProperties.enableCORSForAPI(false);

            // when
            mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

            // then - correct response written to ChannelHandlerContext
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Expose-Methods"), nullValue());
            assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), nullValue());
            assertThat(httpServletResponse.getHeader("X-CORS"), nullValue());
        } finally {
            ConfigurationProperties.enableCORSForAPI(originalValue);
        }
    }

    @Test
    public void shouldAddCORSHeadersToBindRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/bind");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToExpectationRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/expectation");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToResetRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/reset");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToDumpToLogRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/dumpToLog");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToRetriveRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/retrieve");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifyRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/verify");
        when(mockRequestLogFilter.verify(any(Verification.class))).thenReturn("some_verification_response");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToVerifySequenceRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/verifySequence");
        when(mockRequestLogFilter.verify(any(VerificationSequence.class))).thenReturn("some_verification_response");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

    @Test
    public void shouldAddCORSHeadersToStopRequest() {
        // given - a request
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest request = request().withMethod("PUT").withPath("/stop");
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);

        // when
        mockServerServlet.service(new MockHttpServletRequest(), httpServletResponse);

        // then - correct response written to ChannelHandlerContext
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Origin"), is("*"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Methods"), is("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE"));
        assertThat(httpServletResponse.getHeader("Access-Control-Allow-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Expose-Headers"), is("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary"));
        assertThat(httpServletResponse.getHeader("Access-Control-Max-Age"), is("1"));
        assertThat(httpServletResponse.getHeader("X-CORS"), is("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false"));
    }

}
