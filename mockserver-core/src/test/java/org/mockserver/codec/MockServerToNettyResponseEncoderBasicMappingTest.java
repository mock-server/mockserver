package org.mockserver.codec;

import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class MockServerToNettyResponseEncoderBasicMappingTest {

    private MockServerHttpToNettyResponseEncoder mockServerResponseEncoder;
    private List<Object> output;
    private HttpResponse httpResponse;
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Before
    public void setupFixture() {
        mockServerResponseEncoder = new MockServerHttpToNettyResponseEncoder(mockServerLogger);
        output = new ArrayList<>();
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
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
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
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder(CONTENT_LENGTH.toString()));
        assertThat(headers.get("Content-Length"), is("0"));
    }

    @Test
    public void shouldEncodeCookies() {
        // given
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
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
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder(CONTENT_LENGTH.toString()));
        assertThat(headers.get("Content-Length"), is("0"));
    }

    @Test
    public void shouldEncodeStatusCode() {
        // given
        httpResponse.withStatusCode(10);

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.status().code(), is(10));
    }

    @Test
    public void shouldEncodeNoStatusCode() {
        // given
        httpResponse.withStatusCode(null);

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.status().code(), is(200));
    }

    @Test
    public void shouldEncodeReasonPhrase() {
        // given
        httpResponse.withReasonPhrase("someReasonPhrase");

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.status().reasonPhrase(), is("someReasonPhrase"));
    }

    @Test
    public void shouldEncodeNoReasonPhrase() {
        // given
        httpResponse.withReasonPhrase(null);

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.status().reasonPhrase(), is("OK"));
    }

    @Test
    public void shouldEncodeNoReasonPhraseAndStatusCode() {
        // given
        httpResponse.withStatusCode(404);

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.status().reasonPhrase(), is("Not Found"));
    }

    @Test
    public void shouldEncodeStringBody() {
        // given
        httpResponse.withBody("somebody");

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().toString(DEFAULT_HTTP_CHARACTER_SET), is("somebody"));
    }

    @Test
    public void shouldEncodeChunkedStringBody() {
        // given
        httpResponse.withBody("somebody");
        httpResponse.withConnectionOptions(connectionOptions().withChunkSize(3));

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        DefaultHttpResponse responseHeaders = (DefaultHttpResponse) output.get(0);
        assertThat(responseHeaders.status(), is(HttpResponseStatus.OK));
        assertThat(responseHeaders.protocolVersion(), is(HttpVersion.HTTP_1_1));
        assertThat(responseHeaders.headers().getAll("transfer-encoding"), containsInAnyOrder("chunked"));
        DefaultHttpContent chunkOne = (DefaultHttpContent) output.get(1);
        assertThat(new String(chunkOne.content().array()), is("som"));
        DefaultHttpContent chunkTwo = (DefaultHttpContent) output.get(2);
        assertThat(new String(chunkTwo.content().array()), is("ebo"));
        DefaultLastHttpContent lastChunk = (DefaultLastHttpContent) output.get(3);
        assertThat(new String(lastChunk.content().array()), is("dy"));
    }

    @Test
    public void shouldEncodeBinaryBody() {
        // given
        httpResponse.withBody(binary("somebody".getBytes(UTF_8)));

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array()), is("somebody"));
        assertThat(fullHttpResponse.headers().getAll("transfer-encoding"), empty());
    }

    @Test
    public void shouldEncodeNullBody() {
        // given
        httpResponse.withBody((String) null);

        // when
        new MockServerHttpToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().toString(DEFAULT_HTTP_CHARACTER_SET), is(""));
    }

}
