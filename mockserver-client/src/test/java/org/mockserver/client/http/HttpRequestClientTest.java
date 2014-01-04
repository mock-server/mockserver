package org.mockserver.client.http;

import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.jetty.HttpClientResponseMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.streams.IOStreamUtils;

import java.io.EOFException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpRequestClientTest {

    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private Request mockRequest;
    @Mock
    private HttpClientResponseMapper httpClientResponseMapper;
    @InjectMocks
    private HttpRequestClient httpRequestClient;

    @Before
    public void setupTestFixture() throws Exception {
//        mockHttpClient = new HttpClient();
        httpRequestClient = new HttpRequestClient();

        initMocks(this);

        // - an http client that can create a request
        when(mockHttpClient.newRequest(anyString())).thenReturn(mockRequest);
        // - a request that has a fluent API
        when(mockRequest.method(any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.content(any(StringContentProvider.class))).thenReturn(mockRequest);
    }

    @Test
    public void shouldSendPUTRequest() throws Exception {
        // when
        httpRequestClient.sendPUTRequest("baseUri", "body", "/path");
        // then
        verify(mockHttpClient).newRequest("baseUri/path");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new ComparableStringContentProvider("body", StandardCharsets.UTF_8));
        verify(mockRequest).send();
    }

    @Test
    public void shouldSendHttpRequest() throws Exception {
        // given
        when(mockRequest.getHeaders()).thenReturn(new HttpFields());
        ArgumentCaptor<Response.ContentListener> contentListenerArgumentCaptor = ArgumentCaptor.forClass(Response.ContentListener.class);
        ArgumentCaptor<Response.CompleteListener> completeListenerArgumentCaptor = ArgumentCaptor.forClass(Response.CompleteListener.class);
        Response mockResponse = mock(Response.class);

        // when
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                httpRequestClient.sendRequest(
                        new HttpRequest()
                                .withURL("url")
                                .withMethod("POST")
                                .withBody("body")
                                .withHeaders(
                                        new Header("header_name_one", "header_value_one_one", "header_value_one_two"),
                                        new Header("header_name_two", "header_value_two")
                                )
                                .withCookies(
                                        new Cookie("cookie_name_one", "cookie_value_one_one", "cookie_value_one_two"),
                                        new Cookie("cookie_name_two", "cookie_value_two")
                                )
                );
            }
        });
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(1));

        // then
        // - basic request building
        verify(mockHttpClient).newRequest("url");
        verify(mockRequest).method(HttpMethod.POST);
        verify(mockRequest).content(new ComparableStringContentProvider("body", StandardCharsets.UTF_8));
        verify(mockRequest).header("header_name_one", "header_value_one_one");
        verify(mockRequest).header("header_name_one", "header_value_one_two");
        verify(mockRequest).header("header_name_two", "header_value_two");
        verify(mockRequest).header("Cookie", "cookie_name_one=cookie_value_one_one; cookie_name_one=cookie_value_one_two; cookie_name_two=cookie_value_two; ");
        verify(mockRequest).header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        verify(mockRequest).send();
        // - response listener
        verify(mockRequest).onResponseContent(contentListenerArgumentCaptor.capture());
        contentListenerArgumentCaptor.getValue().onContent(null, IOStreamUtils.createBasicByteBuffer("chunk"));
        // - complete listener
        verify(mockRequest).onComplete(completeListenerArgumentCaptor.capture());
        completeListenerArgumentCaptor.getValue().onComplete(new Result(mockRequest, mockResponse));
        // - reading result
        TimeUnit.SECONDS.sleep(1);
        verify(httpClientResponseMapper).mapHttpClientResponseToHttpResponse(same(mockResponse), aryEq("chunk".getBytes()));
    }

    @Test(expected = ExecutionException.class)
    public void shouldHandleExceptionWhenSendingHttpRequest() throws Exception {
        // given
        when(mockRequest.send()).thenThrow(new RuntimeException());

        // when
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    responseFuture.set(httpRequestClient.sendRequest(
                            new HttpRequest()
                                    .withURL("url")
                                    .withMethod("GET")
                    ));
                } catch (Throwable t) {
                    responseFuture.setException(t);
                }
            }
        });
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(1));

        // then
        responseFuture.get(1, TimeUnit.SECONDS);
    }


    @Test(expected = ExecutionException.class)
    public void shouldHandleExceptionResponseWhenSendingHttpRequest() throws Exception {
        // given
        ArgumentCaptor<Response.CompleteListener> completeListenerArgumentCaptor = ArgumentCaptor.forClass(Response.CompleteListener.class);
        Response mockResponse = mock(Response.class);

        // when
        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    responseFuture.set(httpRequestClient.sendRequest(
                            new HttpRequest()
                                    .withURL("url")
                                    .withMethod("GET")
                    ));
                } catch (Throwable t) {
                    responseFuture.setException(t);
                }
            }
        });
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(1));

        // then
        // - complete listener
        verify(mockRequest).onComplete(completeListenerArgumentCaptor.capture());
        completeListenerArgumentCaptor.getValue().onComplete(new Result(mockRequest, new EOFException(), mockResponse));
        // - reading result
        responseFuture.get(1, TimeUnit.SECONDS);
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenSendingExpectationRequest() throws Exception {
        // given
        when(mockRequest.send()).thenThrow(new Exception());

        // when
        httpRequestClient.sendPUTRequest("baseUri", "body", "/path");
    }
}
