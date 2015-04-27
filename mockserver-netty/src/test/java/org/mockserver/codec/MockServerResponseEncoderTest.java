package org.mockserver.codec;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoderTest {

    private MockServerResponseEncoder mockServerResponseEncoder;
    private List<Object> output;
    private HttpResponse httpResponse;

    @Before
    public void setupFixture() {
        mockServerResponseEncoder = new MockServerResponseEncoder();
        output = new ArrayList<Object>();
        httpResponse = response();
    }

    @Test
    public void shouldEncodeHeaders() {
        // given
        httpResponse = response().withHeaders(
                new Header("headerName1", "headerValue1"),
                new Header("headerName2", "headerValue2_1", "headerValue2_2")
        );

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((DefaultFullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("headerName1"), containsInAnyOrder("headerValue1"));
        assertThat(headers.getAll("headerName2"), containsInAnyOrder("headerValue2_1", "headerValue2_2"));
    }

    @Test
    public void shouldEncodeNoHeaders() {
        // given
        httpResponse = response().withHeaders((Header[]) null);

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((DefaultFullHttpResponse) output.get(0)).headers();
        assertThat(headers, emptyIterable());
    }

    @Test
    public void shouldEncodeCookies() {
        // given
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((DefaultFullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Set-Cookie"), containsInAnyOrder(
                "cookieName1=cookieValue1",
                "cookieName2=cookieValue2"
        ));
    }

    @Test
    public void shouldEncodeNoCookies() {
        // given
        httpResponse.withCookies((Cookie[]) null);

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((DefaultFullHttpResponse) output.get(0)).headers();
        assertThat(headers, emptyIterable());
    }

    @Test
    public void shouldEncodeStatusCode() {
        // given
        httpResponse.withStatusCode(10);

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.getStatus().code(), is(10));
    }

    @Test
    public void shouldEncodeNoStatusCode() {
        // given
        httpResponse.withStatusCode(null);

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.getStatus().code(), is(200));
    }

    @Test
    public void shouldEncodeStringBody() {
        // given
        httpResponse.withBody("somebody");

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("somebody"));
    }

    @Test
    public void shouldEncodeBinaryBody() {
        // given
        httpResponse.withBody(binary("somebody".getBytes()));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is("somebody".getBytes()));
    }

    @Test
    public void shouldEncodeNullBody() {
        // given
        httpResponse.withBody((String) null);

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is(""));
    }

    @Test
    public void shouldRespectNonStandardContentTypeHeader() {
        // given
        String body = "A normal string with ASCII characters";
        httpResponse.withBody(body);
        httpResponse.withHeader(new Header("Content-Type", "text/plain; charset=UTF-16"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(body.getBytes(Charset.forName("UTF-16"))));
    }

    @Test
    public void shouldPreferStringBodyCharacterSet() {
        // given
        String body = "Euro sign: €";
        byte[] bodyAsUtf16Bytes = body.getBytes(Charset.forName("UTF-16"));
        // specifying a character set in withBody() should override any Content-Type header
        httpResponse.withBody(body, Charset.forName("UTF-16"));
        httpResponse.withHeader(new Header("Content-Type", "text/plain; charset=US-ASCII"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(bodyAsUtf16Bytes));
    }

    @Test
    public void shouldRespectUtf8ContentTypeHeader() {
        // given
        String body = "Euro sign: \u20AC";
        httpResponse.withBody(body);
        httpResponse.withHeader(new Header("Content-Type", "text/plain; charset=UTF-8"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(body.getBytes(Charset.forName("UTF-8"))));
    }

    @Test
    public void shouldUseDefaultCharsetIfContentTypeAbsent() {
        // given
        // technically the euro sign is not encodable in the default charset, but whatever it is encoded as should be sent
        // over the wire exactly as-is, even if it is default replacement byte
        String body = "Euro sign: \u20AC";
        httpResponse.withBody(body);

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(body.getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetAbsent() {
        // given
        // technically the euro sign is not encodable in the default charset, but whatever it is encoded as should be sent
        // over the wire exactly as-is, even if it is default replacement byte
        String body = "Euro sign: \u20AC";
        httpResponse.withBody(body);
        httpResponse.withHeader(new Header("Content-Type", "text/plain"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(body.getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetNotSupported() {
        // given
        // technically the euro sign is not encodable in the default charset, but whatever it is encoded as should be sent
        // over the wire exactly as-is, even if it is default replacement byte
        String body = "Euro sign: \u20AC";
        httpResponse.withBody(body);
        httpResponse.withHeader(new Header("Content-Type", "text/plain; charset=not-a-real-charset"));

        // when
        new MockServerResponseEncoder().encode(null, httpResponse, output);

        // then
        DefaultFullHttpResponse defaultFullHttpResponse = (DefaultFullHttpResponse) output.get(0);
        assertThat(defaultFullHttpResponse.content().array(), is(body.getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)));
    }
}
