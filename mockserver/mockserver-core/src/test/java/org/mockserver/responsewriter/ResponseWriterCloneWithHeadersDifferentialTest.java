package org.mockserver.responsewriter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpObject;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Differential proof that {@code addConnectionHeader} now copies only the headers
 * (via {@link HttpResponse#cloneWithHeaders()}) instead of taking a full deep {@link
 * HttpResponse#clone()}. This asserts, over an adversarial corpus, that the narrowed copy produces
 * byte-for-byte-identical wire output to the full clone, and that the caller's response is never
 * mutated by the header replacement done on the copy.
 */
public class ResponseWriterCloneWithHeadersDifferentialTest {

    private static final MockServerHttpResponseToFullHttpResponse MAPPER =
        new MockServerHttpResponseToFullHttpResponse(new MockServerLogger());

    private static final class TestResponseWriter extends ResponseWriter {
        private TestResponseWriter() {
            super(Configuration.configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
        }
    }

    private static String encodeToWire(HttpResponse httpResponse) {
        List<DefaultHttpObject> mapped = MAPPER.mapMockServerResponseToNettyResponse(httpResponse);
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseEncoder());
        StringBuilder wire = new StringBuilder();
        try {
            for (DefaultHttpObject object : mapped) {
                channel.writeOutbound(object);
            }
            channel.finish();
            ByteBuf outbound;
            while ((outbound = channel.readOutbound()) != null) {
                try {
                    wire.append(outbound.toString(StandardCharsets.ISO_8859_1));
                } finally {
                    outbound.release();
                }
            }
        } finally {
            channel.releaseOutbound();
            channel.close();
        }
        return wire.toString();
    }

    private static List<HttpResponse> corpus() {
        List<HttpResponse> corpus = new ArrayList<>();
        // JSON body, several headers
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withHeader("cache-control", "no-cache")
            .withBody("{\"a\":1,\"b\":[2,3]}"));
        // empty body
        corpus.add(response().withStatusCode(204));
        // binary body incl. NUL and 0xFF
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "application/octet-stream")
            .withBody(new byte[]{0, 1, 2, (byte) 0xFF, (byte) 0x80, 0x7F, 10, 13}));
        // body declaring a non-utf8 charset, no content-type header
        corpus.add(response().withStatusCode(200)
            .withBody("£©", StandardCharsets.ISO_8859_1));
        // cookies (Set-Cookie) — the field addConnectionHeader stops deep-copying
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "text/plain")
            .withCookie("session", "abc123")
            .withCookie("tracking", "xyz789")
            .withBody("ok"));
        // trailers — force the chunked/trailers mapping leg
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "text/plain")
            .withTrailer("x-checksum", "deadbeef")
            .withBody("payload"));
        // explicit chunked by connection options
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withConnectionOptions(connectionOptions().withChunkSize(4))
            .withBody("chunk-me-please"));
        // an already-present connection header (the value addConnectionHeader replaces)
        corpus.add(response().withStatusCode(200)
            .withHeader("connection", "keep-alive")
            .withHeader("content-type", "text/plain")
            .withBody("hi"));
        // HTTP/2: a stream id set on the model (must survive the copy)
        corpus.add(response().withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withStreamId(7)
            .withBody("{\"x\":1}"));
        // JSON body declaring utf-8 explicitly
        corpus.add(response().withStatusCode(201)
            .withBody("{\"created\":true}", MediaType.APPLICATION_JSON_UTF_8));
        return corpus;
    }

    @Test
    public void cloneWithHeadersProducesIdenticalWireOutputToFullClone() {
        for (HttpResponse original : corpus()) {
            String viaFullClone = encodeToWire(original.clone());
            String viaHeadersOnlyClone = encodeToWire(original.cloneWithHeaders());
            assertThat("wire output must be identical for: " + original,
                viaHeadersOnlyClone, is(viaFullClone));
        }
    }

    @Test
    public void addConnectionHeaderDoesNotMutateCallerResponse() {
        for (boolean keepAlive : new boolean[]{true, false}) {
            HttpResponse original = response().withStatusCode(200)
                .withHeader("content-type", "text/plain")
                .withCookie("session", "abc123")
                .withTrailer("x-checksum", "deadbeef")
                .withBody("body");
            Headers headersBefore = original.getHeaders() == null ? null : original.getHeaders().clone();

            new TestResponseWriter().addConnectionHeader(request().withKeepAlive(keepAlive), original);

            assertThat("caller response must not gain a connection header",
                original.getFirstHeader("connection"), is(""));
            assertThat("caller response headers must be unchanged",
                original.getHeaders(), is(headersBefore));
            assertThat("caller cookies must be intact", original.getCookieList(), hasSize(1));
            assertThat("caller trailers must be intact", original.getTrailerList(), hasSize(1));
        }
    }
}
