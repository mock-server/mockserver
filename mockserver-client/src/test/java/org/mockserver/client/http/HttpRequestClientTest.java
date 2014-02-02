package org.mockserver.client.http;

import org.apache.commons.io.Charsets;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mappers.ApacheHttpClientToMockServerResponseMapper;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpRequestClientTest {

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
        apacheHttpClient = new ApacheHttpClient();
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
    }

    @Test
    public void shouldSendPOSTRequest() throws Exception {
        // when
        apacheHttpClient.sendRequest(new HttpRequest()
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
                .withBody("bodyContent")
        );

        // then
        HttpPost httpPost = (HttpPost) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path?paramOneName=paramOneValueOne&paramOneName=paramOneValueTwo&paramTwoName=paramTwoValue", httpPost.getURI().toString());
        assertEquals("bodyContent", EntityUtils.toString(httpPost.getEntity()));
        assertEquals("POST", httpPost.getMethod());
        assertEquals("headerOneName", httpPost.getAllHeaders()[0].getName());
        assertEquals("headerOneValueOne", httpPost.getAllHeaders()[0].getValue());
        assertEquals("headerOneName", httpPost.getAllHeaders()[1].getName());
        assertEquals("headerOneValueTwo", httpPost.getAllHeaders()[1].getValue());
        assertEquals("headerTwoName", httpPost.getAllHeaders()[2].getName());
        assertEquals("headerTwoValue", httpPost.getAllHeaders()[2].getValue());
        verify(apacheHttpClientToMockServerResponseMapper).mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse);
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
                        new Parameter("paramTwoName", "paramTwoValue")
                )
                .withHeaders(
                        new org.mockserver.model.Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                        new org.mockserver.model.Header("headerTwoName", "headerTwoValue")
                )
        );

        // then
        HttpGet httpPost = (HttpGet) requestArgumentCaptor.getValue();
        assertEquals("http://host:8080/path?paramOneName=paramOneValueOne&paramOneName=paramOneValueTwo&paramTwoName=paramTwoValue", httpPost.getURI().toString());
        assertEquals("GET", httpPost.getMethod());
        assertEquals("headerOneName", httpPost.getAllHeaders()[0].getName());
        assertEquals("headerOneValueOne", httpPost.getAllHeaders()[0].getValue());
        assertEquals("headerOneName", httpPost.getAllHeaders()[1].getName());
        assertEquals("headerOneValueTwo", httpPost.getAllHeaders()[1].getValue());
        assertEquals("headerTwoName", httpPost.getAllHeaders()[2].getName());
        assertEquals("headerTwoValue", httpPost.getAllHeaders()[2].getValue());
        verify(apacheHttpClientToMockServerResponseMapper).mapApacheHttpClientResponseToMockServerResponse(closeableHttpResponse);
    }
}
