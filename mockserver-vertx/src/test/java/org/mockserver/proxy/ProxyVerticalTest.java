package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.HttpServerRequestMapper;
import org.mockserver.mappers.HttpServerResponseMapper;
import org.mockserver.mappers.vertx.HttpClientRequestMapper;
import org.mockserver.mappers.vertx.HttpClientResponseMapper;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.ProxyRequestFilter;
import org.mockserver.proxy.filters.ProxyResponseFilter;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertxtest.http.MockHttpServerRequest;
import org.vertxtest.http.MockHttpServerResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
@Ignore
public class ProxyVerticalTest {

    @Mock
    private HttpServerRequestMapper httpServerRequestMapper;
    @Mock
    private HttpServerResponseMapper httpServerResponseMapper;
    @Mock
    private HttpClientRequestMapper httpClientRequestMapper = new HttpClientRequestMapper();
    @Mock
    private HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
    @Mock
    private HttpClientRequest httpClientRequest;
    @Mock
    private Vertx vertx;
    @Mock
    private HttpClient httpClient;
    @InjectMocks
    private ProxyVertical proxyVertical;
    private MockHttpServerRequest mockHttpServerRequest;
    private MockHttpServerResponse mockHttpServerResponse;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    private ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor;
    private ArgumentCaptor<Handler> handlerArgumentCaptor;

    @Before
    public void setupMocks() throws Exception {
        // create mocks
        proxyVertical = new ProxyVertical();
        initMocks(this);

        // additional mock objects
        mockHttpServerRequest = new MockHttpServerRequest();
        mockHttpServerResponse = new MockHttpServerResponse();
        httpRequest = new HttpRequest().withPath("some_path");
        httpResponse = new HttpResponse();

        // mappers
        when(httpServerRequestMapper.mapHttpServerRequestToHttpRequest(any(MockHttpServerRequest.class), (byte[]) any())).thenReturn(httpRequest);
        httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        doNothing().when(httpClientRequestMapper).mapHttpRequestToHttpClientRequest(httpRequestArgumentCaptor.capture(), same(httpClientRequest));

        // vertx
        when(vertx.createHttpClient()).thenReturn(httpClient);
        when(httpClient.setHost(any(String.class))).thenReturn(httpClient);
        when(httpClient.setPort(anyInt())).thenReturn(httpClient);
        handlerArgumentCaptor = ArgumentCaptor.forClass(Handler.class);
        when(httpClient.request(any(String.class), any(String.class), handlerArgumentCaptor.capture())).thenReturn(httpClientRequest);
    }

    @Test
    public void shouldCallMatchingFiltersBeforeForwardingRequest() throws Exception {
        // given
        // - add first filter
        ProxyRequestFilter filter = mock(ProxyRequestFilter.class);
        proxyVertical.withFilter(httpRequest, filter);
        // - add first filter with other request
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyVertical.withFilter(someOtherRequest, filter);
        // - add second filter
        ProxyRequestFilter someOtherFilter = mock(ProxyRequestFilter.class);
        proxyVertical.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyVertical.getRequestHandler().handle(mockHttpServerRequest);

        // then
        verify(filter, times(1)).onRequest(same(httpRequest));
        verify(filter, times(0)).onRequest(same(someOtherRequest));
        verifyZeroInteractions(someOtherFilter);
    }

    @Test
    public void shouldApplyFiltersBeforeAndAfterRequest() throws Exception {
        // given
        // - add first filter
        ProxyResponseFilter filter = mock(ProxyResponseFilter.class);
        when(filter.onResponse(any(HttpRequest.class), any(HttpResponse.class))).thenReturn(new HttpResponse());
        proxyVertical.withFilter(httpRequest, filter);
        // - add first filter with other request
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyVertical.withFilter(someOtherRequest, filter);
        // - add second filter
        ProxyResponseFilter someOtherFilter = mock(ProxyResponseFilter.class);
        proxyVertical.withFilter(someOtherRequest, someOtherFilter);

        // when
        proxyVertical.getRequestHandler().handle(mockHttpServerRequest);

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
        proxyVertical.getRequestHandler().handle(mockHttpServerRequest);

        // then
        HttpRequest actual = httpRequestArgumentCaptor.getValue();
        assertEquals(1, actual.getHeaders().size());
    }
}
