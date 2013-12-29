package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.ProxyRequestFilter;
import org.mockserver.proxy.filters.ProxyResponseFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class ProxyServletTest {

    @Mock
    private HttpServletRequestMapper httpServletRequestMapper;
    @Mock
    private HttpServletResponseMapper httpServletResponseMapper;
    @Mock
    private HttpRequestClient httpRequestClient;
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

        // setup expectations
        mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletResponse = new MockHttpServletResponse();
        httpRequest = new HttpRequest().withPath("some_path");
        httpResponse = new HttpResponse();
        when(httpServletRequestMapper.createHttpRequest(any(MockHttpServletRequest.class))).thenReturn(httpRequest);
        httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpRequestClient.sendRequest(httpRequestArgumentCaptor.capture())).thenReturn(httpResponse);
    }

    @Test
    public void shouldProxyGETRequest() throws Exception {
        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyHEADRequest() throws Exception {
        // when
        proxyServlet.doHead(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyPOSTRequest() throws Exception {
        // when
        proxyServlet.doPost(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyPUTRequest() throws Exception {
        // when
        proxyServlet.doPut(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyDELETERequest() throws Exception {
        // when
        proxyServlet.doDelete(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyOPTIONSRequest() throws Exception {
        // when
        proxyServlet.doOptions(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldProxyTRACERequest() throws Exception {
        // when
        proxyServlet.doTrace(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(httpServletRequestMapper).createHttpRequest(same(mockHttpServletRequest));
        verify(httpServletResponseMapper).mapHttpServletResponse(same(httpResponse), same(mockHttpServletResponse));
        verify(httpRequestClient).sendRequest(same(httpRequest));
    }

    @Test
    public void shouldCallMatchingFiltersBeforeForwardingRequest() throws Exception {
        // given
        ProxyRequestFilter filter = mock(ProxyRequestFilter.class);
        proxyServlet.withFilter(httpRequest, filter);
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyServlet.withFilter(someOtherRequest, filter);
        ProxyRequestFilter someOtherFilter = mock(ProxyRequestFilter.class);
        proxyServlet.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(filter, times(1)).onRequest(same(httpRequest));
        verify(filter, times(0)).onRequest(same(someOtherRequest));
        verifyZeroInteractions(someOtherFilter);
    }

    @Test
    public void shouldCallMatchingFiltersAfterForwardingRequest() throws Exception {
        // given
        ProxyResponseFilter filter = mock(ProxyResponseFilter.class);
        proxyServlet.withFilter(httpRequest, filter);
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyServlet.withFilter(someOtherRequest, filter);
        ProxyResponseFilter someOtherFilter = mock(ProxyResponseFilter.class);
        proxyServlet.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyServlet.doGet(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(filter, times(1)).onResponse(same(httpRequest), same(httpResponse));
        verify(filter, times(0)).onResponse(same(someOtherRequest), same(httpResponse));
        verifyZeroInteractions(someOtherFilter);
    }

    @Test
    public void shouldNotForwardHopByHopHeaders() throws Exception {
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
}
