package org.mockserver.client.http;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
public class HttpRequestClientTest {

    @Mock
    private CloseableHttpClient mockHttpClient;
    //    @Mock
//    private Request mockRequest;
    @InjectMocks
    private ApacheHttpClient apacheHttpClient;

    @Before
    public void setupTestFixture() throws Exception {
//        apacheHttpClient = new ApacheHttpClient();
//        initMocks(this);
//
//        // - an http client that can create a request
//        when(mockHttpClient.newRequest(anyString())).thenReturn(mockRequest);
//        // - a request that has a fluent API
//        when(mockRequest.method(any(HttpMethod.class))).thenReturn(mockRequest);
//        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
//        when(mockRequest.content(any(StringContentProvider.class))).thenReturn(mockRequest);
    }

    @Ignore
    @Test
    public void shouldSendPUTRequest() throws Exception {
//        // when
//        apacheHttpClient.sendPUTRequest("baseUri", "/path", "body");
//        // then
//        verify(mockHttpClient).newRequest("baseUri/path");
//        verify(mockRequest).method(HttpMethod.PUT);
//        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
//        verify(mockRequest).content(new ComparableStringContentProvider("body", Charsets.UTF_8));
//        verify(mockRequest).send();
    }

    @Ignore
    @Test
    public void shouldRemoveExtraSlashesFromURL() throws Exception {
//        // when
//        apacheHttpClient.sendPUTRequest("baseUri/", "/path", "");
//        // then
//        verify(mockHttpClient).newRequest("baseUri/path");
    }

    @Ignore
    @Test
    public void shouldAddMissingSlashesToURL() throws Exception {
//        // when
//        apacheHttpClient.sendPUTRequest("baseUri", "path", "");
//        // then
//        verify(mockHttpClient).newRequest("baseUri/path");
    }

    @Ignore
    @Test
    public void shouldSendHttpRequest() throws Exception {
//        // given
//        when(mockRequest.getHeaders()).thenReturn(new HttpFields());
//        ArgumentCaptor<Response.ContentListener> contentListenerArgumentCaptor = ArgumentCaptor.forClass(Response.ContentListener.class);
//        ArgumentCaptor<Response.CompleteListener> completeListenerArgumentCaptor = ArgumentCaptor.forClass(Response.CompleteListener.class);
//        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
//
//        // when
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                apacheHttpClient.sendRequest(
//                        new HttpRequest()
//                                .withURL("http://www.example.com")
//                                .withMethod("POST")
//                                .withBody("body")
//                                .withHeaders(
//                                        new Header("header_name_one", "header_value_one_one", "header_value_one_two"),
//                                        new Header("header_name_two", "header_value_two")
//                                )
//                                .withCookies(
//                                        new Cookie("cookie_name_one", "cookie_value_one_one", "cookie_value_one_two"),
//                                        new Cookie("cookie_name_two", "cookie_value_two")
//                                )
//                );
//            }
//        });
//        thread.start();
//        thread.join(TimeUnit.SECONDS.toMillis(1));
//
//        // then
//        // - basic request building
//        verify(mockHttpClient).newRequest("http://www.example.com");
//        verify(mockRequest).method(HttpMethod.POST);
//        verify(mockRequest).content(new ComparableStringContentProvider("body", Charsets.UTF_8));
//        verify(mockRequest).header("header_name_one", "header_value_one_one");
//        verify(mockRequest).header("header_name_one", "header_value_one_two");
//        verify(mockRequest).header("header_name_two", "header_value_two");
//        verify(mockRequest).header("Cookie", "cookie_name_one=cookie_value_one_one; cookie_name_one=cookie_value_one_two; cookie_name_two=cookie_value_two; ");
//        verify(mockRequest).header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
//        verify(mockRequest).send();
//        // - response listener
//        verify(mockRequest).onResponseContent(contentListenerArgumentCaptor.capture());
//        contentListenerArgumentCaptor.getValue().onContent(null, IOStreamUtils.createBasicByteBuffer("chunk"));
//        // - complete listener
//        verify(mockRequest).onComplete(completeListenerArgumentCaptor.capture());
//        completeListenerArgumentCaptor.getValue().onComplete(new Result(mockRequest, mockResponse));
//        // - reading result
//        TimeUnit.SECONDS.sleep(1);
//        verify(httpClientResponseMapper).mapHttpClientResponseToHttpResponse(same(mockResponse), aryEq("chunk".getBytes()));
    }

    @Ignore
    @Test(expected = ExecutionException.class)
    public void shouldHandleExceptionResponseWhenSendingHttpRequest() throws Exception {
//        // given
//        ArgumentCaptor<Response.CompleteListener> completeListenerArgumentCaptor = ArgumentCaptor.forClass(Response.CompleteListener.class);
//        Response mockResponse = mock(Response.class);
//
//        // when
//        final SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    responseFuture.set(apacheHttpClient.sendRequest(
//                            new HttpRequest()
//                                    .withURL("http://www.example.com")
//                                    .withMethod("GET")
//                    ));
//                } catch (Throwable t) {
//                    responseFuture.setException(t);
//                }
//            }
//        });
//        thread.start();
//        thread.join(TimeUnit.SECONDS.toMillis(1));
//
//        // then
//        // - complete listener
//        verify(mockRequest).onComplete(completeListenerArgumentCaptor.capture());
//        completeListenerArgumentCaptor.getValue().onComplete(new Result(mockRequest, new EOFException(), mockResponse));
//        // - reading result
//        responseFuture.get(1, TimeUnit.SECONDS);
    }

    @Ignore
    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionResponseWhenStartingHttpClient() throws Exception {
//        // given
//        ApacheHttpClient apacheHttpClient = spy(new ApacheHttpClient());
//        when(apacheHttpClient.newHttpClient()).thenReturn(mockHttpClient);
//        doThrow(new RuntimeException("TEST EXCEPTION")).when(mockHttpClient).setConnectTimeout(maxTimeout());
//
//        // when
//        apacheHttpClient.sendRequest(
//                new HttpRequest()
//                        .withURL("http://www.example.com")
//                        .withMethod("GET")
//        );
    }

    @Ignore
    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenSendingHttpRequest() throws Exception {
//        // given
//        when(mockRequest.send()).thenThrow(new TimeoutException("TEST EXCEPTION"));
//
//        // when
//        apacheHttpClient.sendRequest(
//                new HttpRequest()
//                        .withURL("http://www.example.com")
//                        .withMethod("GET")
//        );
    }

    @Ignore
    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenSendingExpectationRequest() throws Exception {
//        // given
//        when(mockRequest.send()).thenThrow(new TimeoutException("TEST EXCEPTION"));
//
//        // when
//        apacheHttpClient.sendPUTRequest("baseUri", "/path", "body");
    }
}
