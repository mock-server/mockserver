package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestFilter;
import org.mockserver.filters.ResponseFilter;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.filters.LogFilter;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class ProxyServletTest {

    @Mock
    private HttpServletToMockServerRequestMapper mockHttpServletToMockServerRequestMapper;
    @Mock
    private MockServerToHttpServletResponseMapper mockMockServerToHttpServletResponseMapper;
    @Mock
    private ApacheHttpClient mockApacheHttpClient;
    @Mock
    private LogFilter mockLogFilter;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
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
        httpRequest = new HttpRequest().withPath("some_path");
        httpResponse = new HttpResponse();

        // mappers
        when(mockHttpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(any(MockHttpServletRequest.class))).thenReturn(httpRequest);
        httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockApacheHttpClient.sendRequest(httpRequestArgumentCaptor.capture(), eq(false))).thenReturn(httpResponse);
    }

    @Test
    public void shouldProxyGETRequest() {
        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyHEADRequest() {
        // when
        proxyServlet.doHead(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyPOSTRequest() {
        // when
        proxyServlet.doPost(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyPUTRequest() {
        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyDELETERequest() {
        // when
        proxyServlet.doDelete(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyOPTIONSRequest() {
        // when
        proxyServlet.doOptions(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldProxyTRACERequest() {
        // when
        proxyServlet.doTrace(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletToMockServerRequestMapper).mapHttpServletRequestToMockServerRequest(same(mockHttpServletRequest));
        verify(mockMockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(mockApacheHttpClient).sendRequest(same(httpRequest), eq(false));
    }

    @Test
    public void shouldCallMatchingFiltersBeforeForwardingRequest() {
        // given
        // - add first filter
        RequestFilter filter = mock(RequestFilter.class);
        proxyServlet.withFilter(httpRequest, filter);
        // - add first filter with other request
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyServlet.withFilter(someOtherRequest, filter);
        // - add second filter
        RequestFilter someOtherFilter = mock(RequestFilter.class);
        proxyServlet.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(filter, times(1)).onRequest(same(httpRequest));
        verify(filter, times(0)).onRequest(same(someOtherRequest));
        verifyZeroInteractions(someOtherFilter);
    }

    @Test
    public void shouldApplyFiltersBeforeAndAfterRequest() {
        // given
        // - add first filter
        ResponseFilter filter = mock(ResponseFilter.class);
        when(filter.onResponse(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(new HttpResponse());
        proxyServlet.withFilter(httpRequest, filter);
        // - add first filter with other request
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyServlet.withFilter(someOtherRequest, filter);
        // - add second filter
        ResponseFilter someOtherFilter = mock(ResponseFilter.class);
        proxyServlet.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(filter, times(1)).onResponse(same(httpRequest), same(httpResponse));
        verify(filter, times(0)).onResponse(same(someOtherRequest), same(httpResponse));
        verifyZeroInteractions(someOtherFilter);
    }

    @Test
    public void shouldNotForwardHopByHopHeaders() {
        // given
        httpRequest.withHeaders(
                new Header("some_other_header"),
                new Header("proxy-connection"),
                new Header("connection"),
                new Header("keep-alive"),
                new Header("transfer-encoding"),
                new Header("te"),
                new Header("trailer"),
                new Header("proxy-authorization"),
                new Header("proxy-authenticate"),
                new Header("upgrade")
        );

        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        HttpRequest actual = httpRequestArgumentCaptor.getValue();
        assertEquals(actual.getHeaders().size(), 1);
    }

    @Test
    public void shouldDumpToLogAsJSON() {
        // given
        mockHttpServletRequest.setRequestURI("/dumpToLog");
        mockHttpServletRequest.setContent("body".getBytes());
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).dumpToLog(httpRequest, false);
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldDumpToLogAsJava() {
        // given
        mockHttpServletRequest.setRequestURI("/dumpToLog");
        mockHttpServletRequest.setContent("body".getBytes());
        mockHttpServletRequest.addParameter("type", "java");
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).dumpToLog(httpRequest, true);
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldRetrieve() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = new Expectation[]{};
        mockHttpServletRequest.setRequestURI("/retrieve");
        mockHttpServletRequest.setContent("body".getBytes());
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);
        when(mockLogFilter.retrieve(httpRequest)).thenReturn(expectations);
        when(mockExpectationSerializer.serialize(aryEq(new Expectation[]{}))).thenReturn("expectationsArray");

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).retrieve(httpRequest);
        assertEquals(HttpStatusCode.OK_200.code(), mockHttpServletResponse.getStatus());
        assertEquals("expectationsArray", mockHttpServletResponse.getContentAsString());
    }

    @Test
    public void shouldVerifyRequestNotMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/verify");
        Verification verification = new Verification().withRequest(new HttpRequest()).withTimes(VerificationTimes.once());

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(mockVerificationSerializer.deserialize(requestBytes)).thenReturn(verification);
        when(mockLogFilter.verify(verification)).thenReturn("verification_error");

        // when
        proxyServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is("verification_error"));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.NOT_ACCEPTABLE_406.code()));
        verifyNoMoreInteractions(mockHttpServletToMockServerRequestMapper);
    }

    @Test
    public void shouldVerifyRequestMatching() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/verify");
        Verification verification = new Verification().withRequest(new HttpRequest()).withTimes(VerificationTimes.once());

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(mockVerificationSerializer.deserialize(requestBytes)).thenReturn(verification);
        when(mockLogFilter.verify(verification)).thenReturn("");

        // when
        proxyServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockLogFilter).verify(verification);
        assertThat(httpServletResponse.getContentAsString(), is(""));
        assertThat(httpServletResponse.getStatus(), is(HttpStatusCode.ACCEPTED_202.code()));
        verifyNoMoreInteractions(mockHttpServletToMockServerRequestMapper);
    }

    @Test
    public void shouldReset() {
        // given
        mockHttpServletRequest.setRequestURI("/reset");

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).reset();
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldClear() {
        // given
        mockHttpServletRequest.setRequestURI("/clear");
        mockHttpServletRequest.setContent("body".getBytes());
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).clear(httpRequest);
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldMapPathWhenContextSet() {
        // given
        mockHttpServletRequest.setPathInfo("/clear");
        mockHttpServletRequest.setContextPath("/mockserver");
        mockHttpServletRequest.setRequestURI("/mockserver/clear");
        mockHttpServletRequest.setContent("body".getBytes());
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).clear(httpRequest);
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }

    @Test
    public void shouldMapPathWhenContextSetButPathNull() {
        // given
        mockHttpServletRequest.setPathInfo(null);
        mockHttpServletRequest.setContextPath("/mockserver");
        mockHttpServletRequest.setRequestURI("/clear");
        mockHttpServletRequest.setContent("body".getBytes());
        when(mockHttpRequestSerializer.deserialize("body")).thenReturn(httpRequest);

        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockLogFilter).clear(httpRequest);
        assertEquals(HttpStatusCode.ACCEPTED_202.code(), mockHttpServletResponse.getStatus());
    }
}
