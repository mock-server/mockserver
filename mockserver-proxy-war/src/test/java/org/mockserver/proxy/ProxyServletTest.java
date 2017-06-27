package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;

public class ProxyServletTest {

    @Mock
    private HttpServletRequestToMockServerRequestDecoder mockHttpServletRequestToMockServerRequestDecoder;
    @Mock
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;
    @Mock
    private NettyHttpClient mockNettyHttpClient;
    @Mock
    private RequestLogFilter requestLogFilter;
    @Mock
    private RequestResponseLogFilter requestResponseLogFilter;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @InjectMocks
    private ProxyServlet proxyServlet;
    private MockHttpServletRequest mockHttpServletRequest;
    private MockHttpServletResponse mockHttpServletResponse;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    private ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor;

    @Before
    public void setupMocks() {
        // create mocks
        proxyServlet = new ProxyServlet();
        initMocks(this);

        // additional mock objects
        mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletResponse = new MockHttpServletResponse();
        httpRequest = request().withHeader(header("Host", "localhost")).withPath("some_path");
        httpResponse = new HttpResponse();

        // mappers
        when(mockHttpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(any(MockHttpServletRequest.class))).thenReturn(httpRequest);
        httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockNettyHttpClient
                .sendRequest(
                        httpRequestArgumentCaptor.capture(),
                        any(InetSocketAddress.class)
                )
        ).thenReturn(httpResponse);
    }

    @Test
    public void shouldProxyGETRequest() {
        // when
        httpRequest.withMethod("GET");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyHEADRequest() {
        // when
        httpRequest.withMethod("HEAD");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyPOSTRequest() {
        // when
        httpRequest.withMethod("POST");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyPUTRequest() {
        // when
        httpRequest.withMethod("PUT");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyDELETERequest() {
        // when
        httpRequest.withMethod("DELETE");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyOPTIONSRequest() {
        // when
        httpRequest.withMethod("OPTIONS");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldProxyTRACERequest() {
        // when
        httpRequest.withMethod("TRACE");
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletRequestToMockServerRequestDecoder).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockServerResponseToHttpServletResponseEncoder).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockNettyHttpClient).sendRequest(eq(httpRequest), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotForwardHopByHopHeaders() {
        // given
        httpRequest.withHeaders(
                header("host", "localhost"),
                header("some_other_header"),
                header("proxy-connection"),
                header("connection"),
                header("keep-alive"),
                header("transfer-encoding"),
                header("te"),
                header("trailer"),
                header("proxy-authorization"),
                header("proxy-authenticate"),
                header("upgrade")
        );

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        HttpRequest actual = httpRequestArgumentCaptor.getValue();
        assertEquals(actual.getHeaders().size(), 2);
    }

    @Test
    public void shouldDumpToLogAsJSON() {
        // given
        httpRequest
                .withPath("/dumpToLog")
                .withMethod("PUT")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(requestResponseLogFilter).dumpToLog(httpRequest, false);
        assertEquals(ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldDumpToLogAsJava() {
        // given
        httpRequest
                .withPath("/dumpToLog")
                .withMethod("PUT")
                .withBody("body")
                .withQueryStringParameter("type", "java");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(requestResponseLogFilter).dumpToLog(httpRequest, true);
        assertEquals(ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldReturnRecordedRequests() throws IOException {
        // given
        httpRequest
                .withPath("/some_request")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        httpRequest
                .withMethod("PUT")
                .withPath("/retrieve")
                .withBody("retrieve_body");
        when(mockHttpRequestSerializer.deserialize("retrieve_body")).thenReturn(request("request_matcher"));
        when(requestLogFilter.retrieve(request("request_matcher"))).thenReturn(new HttpRequest[]{request("retrieved_request")});
        when(mockHttpRequestSerializer.serialize(new HttpRequest[]{request("retrieved_request")})).thenReturn("request_response");

        // when
        proxyServlet.service(mockHttpServletRequest, httpServletResponse);

        // then
        verify(requestLogFilter).retrieve(request("request_matcher"));
        assertThat(httpServletResponse.getContentAsString(), is("request_response"));
        assertThat(httpServletResponse.getStatus(), is(OK_200.code()));
    }

    @Test
    public void shouldVerifyRequestNotMatching() throws IOException {
        // given
        httpRequest
                .withPath("/some_request")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        httpRequest
                .withMethod("PUT")
                .withPath("/verify")
                .withBody("retrieve_body");
        when(mockVerificationSerializer.deserialize("retrieve_body")).thenReturn(verification().withRequest(request("request_to_verify")));
        when(requestLogFilter.verify(verification().withRequest(request("request_to_verify")))).thenReturn("no_match");

        // when
        proxyServlet.service(mockHttpServletRequest, httpServletResponse);

        // then
        assertThat(httpServletResponse.getContentAsString(), is("no_match"));
        assertThat(httpServletResponse.getStatus(), is(NOT_ACCEPTABLE_406.code()));
    }

    @Test
    public void shouldVerifyRequestMatching() throws IOException {
        // given
        httpRequest
                .withPath("/some_request")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        httpRequest
                .withMethod("PUT")
                .withPath("/verify")
                .withBody("retrieve_body");
        when(mockVerificationSerializer.deserialize("retrieve_body")).thenReturn(verification().withRequest(request("request_to_verify")));
        when(requestLogFilter.verify(verification().withRequest(request("request_to_verify")))).thenReturn("");

        // when
        proxyServlet.service(mockHttpServletRequest, httpServletResponse);

        // then
        assertThat(httpServletResponse.getContentAsString(), is(""));
        assertThat(httpServletResponse.getStatus(), is(ACCEPTED_202.code()));
    }

    @Test
    public void shouldVerifySequenceRequestNotMatching() throws IOException {
        // given
        httpRequest
                .withPath("/some_request")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        httpRequest
                .withMethod("PUT")
                .withPath("/verifySequence")
                .withBody("retrieve_body");
        when(mockVerificationSequenceSerializer.deserialize("retrieve_body")).thenReturn(verificationSequence().withRequests(request("request_to_verify")));
        when(requestLogFilter.verify(verificationSequence().withRequests(request("request_to_verify")))).thenReturn("no_match");

        // when
        proxyServlet.service(mockHttpServletRequest, httpServletResponse);

        // then
        assertThat(httpServletResponse.getContentAsString(), is("no_match"));
        assertThat(httpServletResponse.getStatus(), is(NOT_ACCEPTABLE_406.code()));
    }

    @Test
    public void shouldVerifySequenceRequestMatching() throws IOException {
        // given
        httpRequest
                .withPath("/some_request")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        httpRequest
                .withMethod("PUT")
                .withPath("/verifySequence")
                .withBody("retrieve_body");
        when(mockVerificationSequenceSerializer.deserialize("retrieve_body")).thenReturn(verificationSequence().withRequests(request("request_to_verify")));
        when(requestLogFilter.verify(verificationSequence().withRequests(request("request_to_verify")))).thenReturn("");

        // when
        proxyServlet.service(mockHttpServletRequest, httpServletResponse);

        // then
        assertThat(httpServletResponse.getContentAsString(), is(""));
        assertThat(httpServletResponse.getStatus(), is(ACCEPTED_202.code()));
    }

    @Test
    public void shouldReset() {
        // given
        httpRequest
                .withPath("/reset")
                .withMethod("PUT");
        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(requestLogFilter).reset();
        assertEquals(ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldClear() {
        // given
        httpRequest
                .withPath("/clear")
                .withMethod("PUT")
                .withBody("body");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.service(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(requestLogFilter).clear(httpRequest);
        assertEquals(ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }
}
