package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.metrics.remotewrite.SnappyBlock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.codec.StreamingAwareHttpObjectAggregator.requestExpectsStreamingResponse;

/**
 * The CONNECT/SOCKS relay forwards a request body still in its {@code Content-Encoding}, so its streaming intent
 * ({@code "stream": true}) is found by scanning the body as it decompresses, without changing or keeping it.
 */
public class StreamingAwareHttpObjectAggregatorStreamingIntentTest {

    private static final byte[] STREAMING = "{\"model\":\"x\",\"stream\": true}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_STREAMING = "{\"model\":\"x\",\"stream\": false}".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_DECODED = 10 * 1024 * 1024;

    @Test
    public void shouldFindStreamTrueInAGzipBody() throws IOException {
        assertThat(expectsStreaming("gzip", gzip(STREAMING), MAX_DECODED), is(true));
        assertThat(expectsStreaming("gzip", gzip(NOT_STREAMING), MAX_DECODED), is(false));
    }

    @Test
    public void shouldFindStreamTrueInARawSnappyBlockBody() {
        assertThat(expectsStreaming("snappy", SnappyBlock.compress(STREAMING), MAX_DECODED), is(true));
    }

    @Test
    public void shouldFindStreamTrueAfterALongMessageHistory() throws IOException {
        byte[] body = withHistory(500_000);

        assertThat(expectsStreaming("gzip", gzip(body), MAX_DECODED), is(true));
    }

    @Test
    public void shouldStopScanningAtTheDecodedSizeLimit() throws IOException {
        byte[] body = withHistory(500_000);

        assertThat(expectsStreaming("gzip", gzip(body), 100_000), is(false));
    }

    @Test
    public void shouldNotTreatALimitOfZeroOrLessAsNoLimit() throws IOException {
        byte[] body = withHistory(500_000);

        assertThat(expectsStreaming("gzip", gzip(body), 0), is(false));
        assertThat(expectsStreaming("gzip", gzip(body), -1), is(false));
        assertThat(expectsStreaming("snappy", SnappyBlock.compress(STREAMING), 0), is(false));
    }

    @Test
    public void shouldFindAMatchSplitBetweenTwoPieces() {
        // a coding that is not decompressed passes through in 1 KiB slices, so the match straddles a slice boundary
        byte[] body = new byte[2048];
        Arrays.fill(body, (byte) ' ');
        byte[] match = "\"stream\": true".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(match, 0, body, 1024 - 5, match.length);

        assertThat(expectsStreaming("compress", body, MAX_DECODED), is(true));
    }

    @Test
    public void shouldNotSignalStreamingForACorruptBody() {
        assertThat(expectsStreaming("gzip", "not gzip at all, \"stream\": true".getBytes(StandardCharsets.UTF_8), MAX_DECODED), is(false));
    }

    @Test
    public void shouldLeaveTheRelayedBytesUntouched() throws IOException {
        byte[] wire = gzip(STREAMING);
        FullHttpRequest request = request("gzip", wire);
        try {
            assertThat(requestExpectsStreamingResponse(request, MAX_DECODED), is(true));

            assertThat(request.content().refCnt(), is(1));
            assertThat(request.content().readerIndex(), is(0));
            byte[] after = new byte[request.content().readableBytes()];
            request.content().getBytes(0, after);
            assertThat(after, is(wire));
        } finally {
            request.release();
        }
    }

    private static boolean expectsStreaming(String contentEncoding, byte[] body, int maxDecodedSize) {
        FullHttpRequest request = request(contentEncoding, body);
        try {
            return requestExpectsStreamingResponse(request, maxDecodedSize);
        } finally {
            request.release();
        }
    }

    private static FullHttpRequest request(String contentEncoding, byte[] body) {
        ByteBuf content = Unpooled.copiedBuffer(body);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/v1/responses", content);
        request.headers().set("content-type", "application/json");
        request.headers().set("content-encoding", contentEncoding);
        return request;
    }

    private static byte[] withHistory(int historyBytes) {
        StringBuilder json = new StringBuilder("{\"model\":\"x\",\"input\":[");
        for (int i = 0; json.length() < historyBytes; i++) {
            json.append("{\"role\":\"user\",\"content\":\"message ").append(i).append(' ').append(Integer.toHexString(i * 7919)).append("\"},");
        }
        return json.append("{\"role\":\"user\",\"content\":\"hi\"}],\"stream\": true}").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gzip(byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(plain);
        }
        return out.toByteArray();
    }

    @Test
    public void shouldSignalStreamingForGeminisStreamingMethodWhateverTheBody() {
        FullHttpRequest streaming = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
            "/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=abc", Unpooled.copiedBuffer(NOT_STREAMING));
        FullHttpRequest whole = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
            "/v1beta/models/gemini-2.5-flash:generateContent", Unpooled.copiedBuffer(NOT_STREAMING));
        try {
            assertThat(requestExpectsStreamingResponse(streaming, MAX_DECODED), is(true));
            assertThat(requestExpectsStreamingResponse(whole, MAX_DECODED), is(false));
        } finally {
            streaming.release();
            whole.release();
        }
    }

    @Test
    public void shouldReadTheRawPathOfAUriWithAMalformedEscapeRatherThanThrow() {
        FullHttpRequest malformed = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
            "/files/100%?key=abc", Unpooled.copiedBuffer(NOT_STREAMING));
        FullHttpRequest malformedStreaming = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
            "/v1beta/models/a%zz:streamGenerateContent", Unpooled.copiedBuffer(NOT_STREAMING));
        try {
            assertThat(requestExpectsStreamingResponse(malformed, MAX_DECODED), is(false));
            assertThat(requestExpectsStreamingResponse(malformedStreaming, MAX_DECODED), is(true));
        } finally {
            malformed.release();
            malformedStreaming.release();
        }
    }
}
