package org.mockserver.client.http;

import org.apache.commons.io.Charsets;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.ApacheHttpClientToMockServerResponseMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ApacheHttpClientTest {

    ArgumentCaptor<HttpUriRequest> requestArgumentCaptor;
    @Mock
    private CloseableHttpResponse closeableHttpResponse;
    @Mock
    private ApacheHttpClientToMockServerResponseMapper apacheHttpClientToMockServerResponseMapper;
    @Mock
    private CloseableHttpClient httpClient;
    @InjectMocks
    private ApacheHttpClient apacheHttpClient;

    @Before
    public void setupTestFixture() throws Exception {
        apacheHttpClient = new ApacheHttpClient(true);
        initMocks(this);

        requestArgumentCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
        when(httpClient.execute(requestArgumentCaptor.capture())).thenReturn(closeableHttpResponse);
    }

    @Test
    public void shouldSendPUTRequest() throws Exception {
        // given
        when(closeableHttpResponse.getEntity()).thenReturn(new StringEntity("bodyContent"));

        // when
        String response = apacheHttpClient.sendPUTRequest("baseUri", "/path", "body");

        // then
        assertEquals("baseUri/path", requestArgumentCaptor.getValue().getURI().toString());
        assertEquals("body", new String(EntityUtils.toByteArray(((HttpPut) requestArgumentCaptor.getValue()).getEntity()), Charsets.UTF_8));
        assertEquals("bodyContent", response);
    }

    @Test
    public void shouldHandleSlashesInUrl() throws Exception {
        // given
        when(closeableHttpResponse.getEntity()).thenReturn(new ByteArrayEntity("bodyContent".getBytes(Charsets.UTF_8)));

        // when
        apacheHttpClient.sendPUTRequest("baseUri/", "/path", "body");

        // then
        assertEquals("baseUri/path", requestArgumentCaptor.getValue().getURI().toString());

        // when
        apacheHttpClient.sendPUTRequest("baseUri/", "/path", "body");

        // then
        assertEquals("baseUri/path", requestArgumentCaptor.getValue().getURI().toString());

        // when
        apacheHttpClient.sendPUTRequest("baseUri/", "path", "body");

        // then
        assertEquals("baseUri/path", requestArgumentCaptor.getValue().getURI().toString());

        // when
        apacheHttpClient.sendPUTRequest("baseUri", "path", "body");

        // then
        assertEquals("baseUri/path", requestArgumentCaptor.getValue().getURI().toString());
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionsWhenSendingPUTRequest() throws Exception {
        // given
        when(closeableHttpResponse.getEntity()).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        apacheHttpClient.sendPUTRequest("baseUri", "/path", "body");
    }

    @Test
    public void shouldMapGETRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("GET"));

        // then
        assertEquals(HttpGet.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapDELETERequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("DELETE"));

        // then
        assertEquals(HttpDelete.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapHEADRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("HEAD"));

        // then
        assertEquals(HttpHead.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapOPTIONSRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("OPTIONS"));

        // then
        assertEquals(HttpOptions.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapPOSTRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("POST"));

        // then
        assertEquals(HttpPost.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapPUTRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("PUT"));

        // then
        assertEquals(HttpPut.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapTRACERequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("TRACE"));

        // then
        assertEquals(HttpTrace.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapPATCHRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("PATCH"));

        // then
        assertEquals(HttpPatch.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldMapINCORRECTRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest().withMethod("INCORRECT"));

        // then
        assertEquals(HttpGet.class, requestArgumentCaptor.getValue().getClass());
    }

    @Test
    public void shouldSendFullPOSTRequest() throws Exception {
        // given
        HttpResponse httpResponse = new HttpResponse().withStatusCode(200).withBody("exampleResponse");
        when(apacheHttpClientToMockServerResponseMapper.mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse)).thenReturn(httpResponse);

        // when
        HttpResponse httpResponseActual = apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http://host:8080/path")
                .withPath("/path")
                .withQueryStringParameters(
                        new Parameter("paramOneName", "paramOneValueOne", "paramOneValueTwo"),
                        new Parameter("paramTwoName", "paramTwoValue")
                )
                .withHeaders(
                        new org.mockserver.model.Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                        new org.mockserver.model.Header("headerTwoName", "headerTwoValue")
                )
                .withCookies(
                        new Cookie("cookieOneName", "cookieOneValueOne", "cookieOneValueTwo"),
                        new Cookie("cookieTwoName", "cookieTwoValue")
                )
                .withBody("bodyContent")
        );

        // then
        HttpPost httpPost = (HttpPost) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path?paramOneName=paramOneValueOne&paramOneName=paramOneValueTwo&paramTwoName=paramTwoValue", httpPost.getURI().toString());
        assertEquals("bodyContent", EntityUtils.toString(httpPost.getEntity()));
        assertEquals("POST", httpPost.getMethod());
        assertEquals(4, httpPost.getAllHeaders().length);
        assertEquals("headerOneName", httpPost.getAllHeaders()[0].getName());
        assertEquals("headerOneValueOne", httpPost.getAllHeaders()[0].getValue());
        assertEquals("headerOneName", httpPost.getAllHeaders()[1].getName());
        assertEquals("headerOneValueTwo", httpPost.getAllHeaders()[1].getValue());
        assertEquals("headerTwoName", httpPost.getAllHeaders()[2].getName());
        assertEquals("headerTwoValue", httpPost.getAllHeaders()[2].getValue());
        assertEquals("Cookie", httpPost.getAllHeaders()[3].getName());
        assertEquals("cookieOneName=cookieOneValueOne; cookieOneName=cookieOneValueTwo; cookieTwoName=cookieTwoValue", httpPost.getAllHeaders()[3].getValue());
        verify(apacheHttpClientToMockServerResponseMapper).mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse);
        assertSame(httpResponse, httpResponseActual);
    }

    @Test
    public void shouldSendBarePOSTRequest() throws Exception {
        // given
        HttpResponse httpResponse = new HttpResponse().withStatusCode(200).withBody("exampleResponse");
        when(apacheHttpClientToMockServerResponseMapper.mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse)).thenReturn(httpResponse);

        // when
        HttpResponse httpResponseActual = apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http://host:8080/path")
        );

        // then
        HttpPost httpPost = (HttpPost) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path", httpPost.getURI().toString());
        assertEquals("POST", httpPost.getMethod());
        verify(apacheHttpClientToMockServerResponseMapper).mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse);
        assertSame(httpResponse, httpResponseActual);
    }

    @Test
    public void shouldSendGETRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("GET")
                .withURL("http://host:8080/path")
                .withPath("/path")
                .withQueryStringParameters(
                        new Parameter("paramOneName", "paramOneValueOne", "paramOneValueTwo"),
                        new Parameter("paramTwoName", "paramTwoValue"),
                        new Parameter("paramThreeName")
                )
                .withHeaders(
                        new org.mockserver.model.Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                        new org.mockserver.model.Header("headerTwoName", "headerTwoValue"),
                        new org.mockserver.model.Header("headerThreeName")
                )
                .withCookies(
                        new Cookie("cookieOneName", "cookieOneValueOne", "cookieOneValueTwo"),
                        new Cookie("cookieTwoName", "cookieTwoValue"),
                        new Cookie("cookieThreeName")
                )
        );

        // then
        HttpGet httpGet = (HttpGet) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path?paramOneName=paramOneValueOne&paramOneName=paramOneValueTwo&paramTwoName=paramTwoValue&paramThreeName=", httpGet.getURI().toString());
        assertEquals("GET", httpGet.getMethod());
        assertEquals(5, httpGet.getAllHeaders().length);
        assertEquals("headerOneName", httpGet.getAllHeaders()[0].getName());
        assertEquals("headerOneValueOne", httpGet.getAllHeaders()[0].getValue());
        assertEquals("headerOneName", httpGet.getAllHeaders()[1].getName());
        assertEquals("headerOneValueTwo", httpGet.getAllHeaders()[1].getValue());
        assertEquals("headerTwoName", httpGet.getAllHeaders()[2].getName());
        assertEquals("headerTwoValue", httpGet.getAllHeaders()[2].getValue());
        assertEquals("headerThreeName", httpGet.getAllHeaders()[3].getName());
        assertEquals("", httpGet.getAllHeaders()[3].getValue());
        assertEquals("Cookie", httpGet.getAllHeaders()[4].getName());
        assertEquals("cookieOneName=cookieOneValueOne; cookieOneName=cookieOneValueTwo; cookieTwoName=cookieTwoValue; cookieThreeName=", httpGet.getAllHeaders()[4].getValue());
    }

    @Test
    public void shouldRemoveTransferEncoding() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http://host:8080/path")
                .withHeaders(
                        new org.mockserver.model.Header(HTTP.TRANSFER_ENCODING, HTTP.CHUNK_CODING),
                        new org.mockserver.model.Header(HTTP.CONTENT_LEN, "0")
                )
                .withBody("bodyContent")
        );

        // then
        HttpPost httpPost = (HttpPost) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path", httpPost.getURI().toString());
        assertEquals("POST", httpPost.getMethod());
        assertEquals(0, httpPost.getAllHeaders().length);
        verify(apacheHttpClientToMockServerResponseMapper).mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse);
    }

    @Test
    public void shouldHandleCircularRedirectException() throws Exception {
        // given
        HttpResponse httpResponse = new HttpResponse().withStatusCode(200).withBody("exampleResponse");
        when(apacheHttpClientToMockServerResponseMapper.mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse)).thenReturn(httpResponse);
        when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("TEST EXCEPTION", new CircularRedirectException("TEST EXCEPTION")));

        // then
        assertEquals(new HttpResponse(), apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http://host:8080/path")
                .withHeaders(
                        new org.mockserver.model.Header(HTTP.CONTENT_LEN, "0")
                )
                .withBody("bodyContent")
        ));
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleIOException() throws Exception {
        // given
        when(httpClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("TEST EXCEPTION"));

        // then
        apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http://host:8080/path")
        );
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleURISyntaxException() throws Exception {
        // then
        apacheHttpClient.sendRequest(new HttpRequest()
                .withMethod("POST")
                .withURL("http\\://this_is_an_invalid_url")
        );
    }
}
