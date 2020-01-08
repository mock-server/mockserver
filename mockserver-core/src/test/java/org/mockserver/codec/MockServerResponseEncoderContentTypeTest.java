package org.mockserver.codec;

import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoderContentTypeTest {

    private List<Object> output;
    private HttpResponse httpResponse;
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Before
    public void setupFixture() {
        output = new ArrayList<>();
        httpResponse = response();
    }

    @Test
    public void shouldDecodeBodyWithContentTypeAndNoCharset() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC");
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").toString()));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().array(), is("avro işarəsi: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldDecodeBodyWithNoContentType() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC");

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().array(), is("avro işarəsi: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldTransmitUnencodableCharacters() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC", DEFAULT_HTTP_CHARACTER_SET);
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").toString()));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().array(), is("avro işarəsi: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetNotSupported() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC");
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), "text/plain; charset=invalid-charset"));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.content().array(), is("avro işarəsi: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldDecodeBodyWithUTF8ContentType() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC", StandardCharsets.UTF_8);
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_8).toString()));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array(), StandardCharsets.UTF_8), is("avro işarəsi: \u20AC"));
    }

    @Test
    public void shouldDecodeBodyWithUTF16ContentType() {
        // given
        httpResponse.withBody("我说中国话", StandardCharsets.UTF_16);
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString()));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array(), StandardCharsets.UTF_16), is("我说中国话"));
    }

    @Test
    public void shouldEncodeStringBodyWithCharset() {
        // given
        httpResponse.withBody("我说中国话", StandardCharsets.UTF_16);

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpRequest = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpRequest.content().array(), StandardCharsets.UTF_16), is("我说中国话"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldEncodeUTF8JsonBodyWithContentType() {
        // given
        httpResponse.withBody("{ \"some_field\": \"我说中国话\" }").withHeader(CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array(), StandardCharsets.UTF_8), is("{ \"some_field\": \"我说中国话\" }"));
        assertThat(fullHttpResponse.headers().get(CONTENT_TYPE), is(MediaType.JSON_UTF_8.toString()));
    }

    @Test
    public void shouldEncodeUTF8JsonBodyWithCharset() {
        // given
        httpResponse.withBody(json("{ \"some_field\": \"我说中国话\" }", StandardCharsets.UTF_8));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array(), StandardCharsets.UTF_8), is("{ \"some_field\": \"我说中国话\" }"));
        assertThat(fullHttpResponse.headers().get(CONTENT_TYPE), is(MediaType.JSON_UTF_8.toString()));
    }

    @Test
    public void shouldPreferStringBodyCharacterSet() {
        // given
        httpResponse.withBody("avro işarəsi: \u20AC", StandardCharsets.UTF_16);
        httpResponse.withHeader(new Header(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(StandardCharsets.US_ASCII).toString()));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(new String(fullHttpResponse.content().array(), StandardCharsets.UTF_16), is("avro işarəsi: \u20AC"));
    }

    @Test
    public void shouldReturnNoDefaultContentTypeWhenNoBodySpecified() {
        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), empty());
    }

    @Test
    public void shouldReturnContentTypeForStringBody() {
        // given - a request & response
        httpResponse.withBody("somebody");

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), empty());
    }

    @Test
    public void shouldReturnContentTypeForStringBodyWithContentType() {
        // given - a request & response
        httpResponse.withBody(exact("somebody", MediaType.PLAIN_TEXT_UTF_8));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("text/plain; charset=utf-8"));
    }

    @Test
    public void shouldReturnContentTypeForStringBodyWithCharset() {
        // given - a request & response
        httpResponse.withBody(exact("somebody", StandardCharsets.UTF_16));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("text/plain; charset=utf-16"));
    }

    @Test
    public void shouldReturnContentTypeForJsonBody() {
        // given
        httpResponse.withBody(json("somebody"));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("application/json"));
    }

    @Test
    public void shouldReturnContentTypeForJsonBodyWithContentType() {
        // given - a request & response
        httpResponse.withBody(json("somebody", MediaType.JSON_UTF_8));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("application/json; charset=utf-8"));
    }

    @Test
    public void shouldReturnContentTypeForBinaryBody() {
        // given
        httpResponse.withBody(binary("somebody".getBytes(UTF_8)));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), empty());
    }

    @Test
    public void shouldReturnContentTypeForBinaryBodyWithContentType() {
        // given - a request & response
        httpResponse.withBody(binary("somebody".getBytes(UTF_8), MediaType.QUICKTIME));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder(MediaType.QUICKTIME.toString()));
    }

    @Test
    public void shouldReturnContentTypeForXmlBody() {
        // given
        httpResponse.withBody(xml("somebody"));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("application/xml"));
    }

    @Test
    public void shouldReturnContentTypeForXmlBodyWithContentType() {
        // given - a request & response
        httpResponse.withBody(xml("somebody", MediaType.XML_UTF_8));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("text/xml; charset=utf-8"));
    }

    @Test
    public void shouldReturnNoContentTypeForBodyWithNoAssociatedContentType() {
        // given
        httpResponse.withBody(xml("some_value", (MediaType) null));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), empty());
    }

    @Test
    public void shouldNotSetDefaultContentTypeWhenContentTypeExplicitlySpecified() {
        // given
        httpResponse
            .withBody(json("somebody"))
            .withHeaders(new Header("Content-Type", "some/value"));

        // when
        new MockServerToNettyResponseEncoder(mockServerLogger).encode(null, httpResponse, output);

        // then
        FullHttpResponse fullHttpResponse = (FullHttpResponse) output.get(0);
        assertThat(fullHttpResponse.headers().getAll("Content-Type"), containsInAnyOrder("some/value"));
    }
}
