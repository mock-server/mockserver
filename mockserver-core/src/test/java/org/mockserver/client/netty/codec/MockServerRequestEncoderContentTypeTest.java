package org.mockserver.client.netty.codec;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.OutboundHttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoderContentTypeTest {

    private List<Object> output;
    private OutboundHttpRequest httpRequest;

    @Before
    public void setupFixture() {
        output = new ArrayList<Object>();
        httpRequest = outboundRequest("localhost", 80, "", request());
    }

    @Test
    public void shouldDecodeBodyWithContentTypeAndNoCharset() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldDecodeBodyWithNoContentType() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldTransmitUnencodableCharacters() {
        // given
        httpRequest.withBody("Euro sign: \u20AC", ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is(new String("Euro sign: \u20AC".getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetNotSupported() {
        // given
        httpRequest.withBody("A normal string with ASCII characters");
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=invalid-charset"));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET), is("A normal string with ASCII characters"));
    }

    @Test
    public void shouldDecodeBodyWithUTF8ContentType() {
        // given
        httpRequest.withBody("avro işarəsi: \u20AC", Charsets.UTF_8);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_8), is("avro işarəsi: \u20AC"));
    }

    @Test
    public void shouldDecodeBodyWithUTF16ContentType() {
        // given
        httpRequest.withBody("我说中国话", Charsets.UTF_16);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("我说中国话"));
    }

    @Test
    public void shouldEncodeStringBodyWithCharset() {
        // given
        httpRequest.withBody("我说中国话", Charsets.UTF_16);

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("我说中国话"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldEncodeJsonBodyWithCharset() {
        // given
        httpRequest.withBody(json("{ \"some_field\": \"我说中国话\" }", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("{ \"some_field\": \"我说中国话\" }"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.JSON_UTF_8.withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldPreferStringBodyCharacterSet() {
        // given
        httpRequest.withBody("avro işarəsi: \u20AC", Charsets.UTF_16);
        httpRequest.withHeader(new Header(HttpHeaders.Names.CONTENT_TYPE, MediaType.create("text", "plain").withCharset(Charsets.US_ASCII).toString()));

        // when
        new MockServerRequestEncoder().encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(Charsets.UTF_16), is("avro işarəsi: \u20AC"));
    }
}
