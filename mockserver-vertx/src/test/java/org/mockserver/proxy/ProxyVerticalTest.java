package org.mockserver.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.VertXToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToVertXResponseMapper;
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
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertxtest.http.MockHttpClientRequest;
import org.vertxtest.http.MockHttpClientResponse;
import org.vertxtest.http.MockHttpServerRequest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("unchecked")
public class ProxyVerticalTest {

    // mappers
    @Mock
    private VertXToMockServerRequestMapper vertXToMockServerRequestMapper;
    @Mock
    private HttpClientRequestMapper httpClientRequestMapper;
    @Mock
    private MockServerToVertXResponseMapper mockServerToVertXResponseMapper;
    @Mock
    private HttpClientResponseMapper httpClientResponseMapper;
    // request & response
    @Mock
    private MockHttpClientRequest mockHttpClientRequest;
    private MockHttpServerRequest mockHttpServerRequest;
    private MockHttpClientResponse mockHttpClientResponse;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    // vertx
    @Mock
    private Vertx vertx;
    @Mock
    private HttpClient httpClient;
    // under test
    @InjectMocks
    private ProxyVertical proxyVertical;
    private ArgumentCaptor<Handler> clientHandlerArgumentCaptor;

    @Before
    public void setupMocks() throws Exception {
        // create mocks
        proxyVertical = new ProxyVertical();
        initMocks(this);

        // additional mock objects
        mockHttpClientRequest = new MockHttpClientRequest();
        mockHttpClientResponse = new MockHttpClientResponse();
        mockHttpServerRequest = new MockHttpServerRequest();
        httpRequest = new HttpRequest().withPath("some_path");
        httpResponse = new HttpResponse();

        // mappers
        when(vertXToMockServerRequestMapper.mapVertXRequestToMockServerRequest(any(MockHttpServerRequest.class), (byte[]) any())).thenReturn(httpRequest);
        when(httpClientResponseMapper.mapHttpClientResponseToHttpResponse(any(HttpClientResponse.class), (byte[]) any())).thenReturn(httpResponse);

        // vertx
        clientHandlerArgumentCaptor = ArgumentCaptor.forClass(Handler.class);
        when(vertx.createHttpClient()).thenReturn(httpClient);
        when(httpClient.setHost(any(String.class))).thenReturn(httpClient);
        when(httpClient.setPort(anyInt())).thenReturn(httpClient);
        when(httpClient.request(any(String.class), any(String.class), clientHandlerArgumentCaptor.capture())).thenReturn(mockHttpClientRequest);
    }

    @Test
    public void shouldProxyRequest() {
        // given
        ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        ArgumentCaptor<HttpServerResponse> httpServerResponseArgumentCaptor = ArgumentCaptor.forClass(HttpServerResponse.class);

        // when
        mockHttpServerRequest.withBody("server_request_body".getBytes());
        mockHttpClientResponse.withBody("client_response_body".getBytes());
        proxyVertical.getRequestHandler().handle(mockHttpServerRequest);
        clientHandlerArgumentCaptor.getValue().handle(mockHttpClientResponse);

        // then
        verify(mockServerToVertXResponseMapper).mapMockServerResponseToVertXResponse(any(HttpResponse.class), httpServerResponseArgumentCaptor.capture());
        assertEquals(mockHttpServerRequest.response(), httpServerResponseArgumentCaptor.getValue());
        verify(httpClientRequestMapper).mapHttpRequestToHttpClientRequest(httpRequestArgumentCaptor.capture(), same(mockHttpClientRequest));
        assertEquals(httpRequest, httpRequestArgumentCaptor.getValue());
    }

    @Test
    public void shouldCallMatchingFiltersBeforeForwardingRequest() throws Exception {
        // given
        // - add first filter
        ProxyRequestFilter filter = mock(ProxyRequestFilter.class);
        proxyVertical.withFilter(httpRequest, filter);
        when(filter.onRequest(httpRequest)).thenReturn(httpRequest);
        // - add first filter with other request
        HttpRequest someOtherRequest = new HttpRequest().withPath("some_other_path");
        proxyVertical.withFilter(someOtherRequest, filter);
        // - add second filter
        ProxyRequestFilter someOtherFilter = mock(ProxyRequestFilter.class);
        when(someOtherFilter.onRequest(httpRequest)).thenReturn(httpRequest);
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
        clientHandlerArgumentCaptor.getValue().handle(mockHttpClientResponse);

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
        ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        // when
        proxyVertical.getRequestHandler().handle(mockHttpServerRequest);

        // then
        verify(httpClientRequestMapper).mapHttpRequestToHttpClientRequest(httpRequestArgumentCaptor.capture(), same(mockHttpClientRequest));
        HttpRequest actual = httpRequestArgumentCaptor.getValue();
        assertEquals(1, actual.getHeaders().size());
    }
}
