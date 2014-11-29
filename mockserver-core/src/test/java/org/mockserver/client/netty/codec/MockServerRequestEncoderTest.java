package org.mockserver.client.netty.codec;

import com.google.common.base.Charsets;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.OutboundHttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoderTest {

    private MockServerRequestEncoder mockServerRequestEncoder;
    private List<Object> output;
    private OutboundHttpRequest httpRequest;

    @Before
    public void setupFixture() {
        mockServerRequestEncoder = new MockServerRequestEncoder();
        output = new ArrayList<Object>();
        httpRequest = outboundRequest("localhost", 80, "", request());
    }

    @Test
    public void shouldEncodeMethod() {
        // given
        httpRequest.withMethod("OPTIONS");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpMethod method = ((FullHttpRequest) output.get(0)).getMethod();
        assertThat(method, is(HttpMethod.OPTIONS));
    }

    @Test
    public void shouldEncodeQueryParameters() {
        // given
        httpRequest
                .withPath("/uri")
                .withQueryStringParameters(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/uri?" +
                "queryStringParameterNameOne=queryStringParameterValueOne_One&" +
                "queryStringParameterNameOne=queryStringParameterValueOne_Two&" +
                "queryStringParameterNameTwo=queryStringParameterValueTwo_One"));
    }

    @Test
    public void shouldEscapeQueryParameters() {
        // given
        httpRequest
                .withPath("/uri")
                .withQueryStringParameters(
                        param("parameter name with spaces", "a value with double \"quotes\" and spaces"),
                        param("another parameter", "a value with single \'quotes\' and spaces")
                );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/uri?" +
                "parameter%20name%20with%20spaces=a%20value%20with%20double%20%22quotes%22%20and%20spaces&" +
                "another%20parameter=a%20value%20with%20single%20%27quotes%27%20and%20spaces"));
    }

    @Test
    public void shouldEncodePath() {
        // given
        httpRequest.withPath("/other_path");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).getUri();
        assertThat(uri, is("/other_path"));
    }

    @Test
    public void shouldEncodeHeaders() {
        // given
        httpRequest.withHeaders(
                new Header("headerName1", "headerValue1"),
                new Header("headerName2", "headerValue2_1", "headerValue2_2")
        );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("headerName1"), containsInAnyOrder("headerValue1"));
        assertThat(headers.getAll("headerName2"), containsInAnyOrder("headerValue2_1", "headerValue2_2"));
    }

    @Test
    public void shouldEncodeDefaultNonSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 80, "", request());

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
    }

    @Test
    public void shouldEncodeNonDefaultNonSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 666, "", request());

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost:666"));
    }

    @Test
    public void shouldEncodeDefaultSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 443, "", request().setSecure(true));

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
    }

    @Test
    public void shouldEncodeNonDefaultSecureHostHeader() {
        // given
        httpRequest = outboundRequest("localhost", 999, "", request().setSecure(true));

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost:999"));
    }

    @Test
    public void shouldEncodeNoHeaders() {
        // given
        httpRequest.withHeaders((Header[]) null);

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder(
                "Host",
                "Accept-Encoding",
                "Content-Length",
                "Connection"
        ));
        assertThat(headers.getAll("Host"), containsInAnyOrder("localhost"));
        assertThat(headers.getAll("Accept-Encoding"), containsInAnyOrder("gzip,deflate"));
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("0"));
        assertThat(headers.getAll("Connection"), containsInAnyOrder("keep-alive"));
    }

    @Test
    public void shouldEncodeCookies() {
        // given
        httpRequest.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), is(Arrays.asList("cookieName1=cookieValue1; cookieName2=cookieValue2")));
    }

    @Test
    public void shouldEncodeNoCookies() {
        // given
        httpRequest.withCookies((Cookie[]) null);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), emptyIterable());
    }

    @Test
    public void shouldEncodeStringBody() {
        // given
        httpRequest.withBody("somebody");

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is("somebody"));
    }

    @Test
    public void shouldEncodeBinaryBody() {
        // given
        httpRequest.withBody(binary("somebody".getBytes()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().array(), is("somebody".getBytes()));
    }

    @Test
    public void shouldEncodeNullBody() {
        // given
        httpRequest.withBody((String) null);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is(""));
    }
}
