package org.mockserver.benchmark;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Protocol;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Inbound decode of a bodiless request carrying a realistic client header set, mapped repeatedly by
 * ONE {@link FullHttpRequestToMockServerHttpRequest} (one connection), as a keep-alive or HTTP/2
 * client drives it. Measures the per-connection header sharing: an equal header name or value at the
 * same position as on the previous request reuses that request's wrapper.
 *
 * <pre>./run.sh -prof gc InboundHeaderSharingBenchmark</pre>
 *
 * <ul>
 *   <li>{@code values=repeated}: eight of ten values repeat on every request and two (a trace id and
 *   a request id) change, which is what real clients send — the sharing hit path;</li>
 *   <li>{@code values=unique}: every value changes on every request — the miss path, where sharing
 *   can only add the comparison cost.</li>
 *   <li>{@code values=reordered}: the values of {@code repeated}, with the header order reversed on
 *   every other request, as some HTTP/2 clients reorder headers — positional misses fall back to the
 *   identity scan of the previous request's headers.</li>
 *   <li>{@code headerCount} adds fixed {@code X-Extra-n} headers after the ten above, e.g.
 *   {@code -p headerCount=64}.</li>
 *   <li>{@code protocol=HTTP_1_1} is what netty's HTTP/1.1 decoder produces: {@code String} values
 *   and {@link AsciiString} names, one shared constant for {@code Host} and {@code Accept} and a fresh
 *   instance per request for every other name; {@code HTTP_2} carries lower-case {@link AsciiString}
 *   names (one instance each, as HPACK-indexed names arrive) and values (a fresh instance per request,
 *   as HPACK literals arrive — the slower case, since an indexed value is matched by identity).</li>
 * </ul>
 * The per-request names and values are drawn from pre-built pools, so the per-op allocation is the
 * netty request plus what the mapper allocates.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class InboundHeaderSharingBenchmark {

    private static final int POOL = 1024;
    private static final String[][] HEADERS = {
        {"Host", "api.example.com:8080"},
        {"User-Agent", "okhttp/4.12.0"},
        {"Accept", "application/json"},
        {"Accept-Encoding", "gzip, deflate, br"},
        {"Accept-Language", "en-GB,en;q=0.9"},
        {"Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiYXVkIjoiYXBpIn0.c2lnbmF0dXJlLXNpZ25hdHVyZQ"},
        {"X-Client-Version", "4.12.7"},
        {"X-Tenant-Id", "tenant-7f3c2a9e"},
        {"traceparent", null},
        {"X-Request-Id", null},
    };

    @Param({"repeated", "unique", "reordered"})
    public String values;

    @Param({"10"})
    public int headerCount;

    @Param({"HTTP_1_1", "HTTP_2"})
    public String protocol;

    private FullHttpRequestToMockServerHttpRequest mapper;
    private CharSequence[][] namePool;
    private CharSequence[][] valuePool;
    private Protocol requestProtocol;
    private boolean reorder;
    private int next;

    @Setup(Level.Trial)
    public void setup() {
        mapper = new FullHttpRequestToMockServerHttpRequest(Configuration.configuration(), new MockServerLogger(), false, null, 1080);
        requestProtocol = Protocol.valueOf(protocol);
        reorder = "reordered".equals(values);
        boolean http2 = requestProtocol == Protocol.HTTP_2;
        String[][] headers = new String[Math.max(headerCount, HEADERS.length)][];
        for (int h = 0; h < headers.length; h++) {
            headers[h] = h < HEADERS.length ? HEADERS[h] : new String[]{"X-Extra-" + h, "extra-value-" + h};
        }
        namePool = new CharSequence[headers.length][POOL];
        valuePool = new CharSequence[headers.length][POOL];
        for (int h = 0; h < headers.length; h++) {
            String name = headers[h][0];
            AsciiString sharedName = AsciiString.of(http2 ? name.toLowerCase(Locale.ROOT) : name);
            boolean decoderConstant = "Host".equals(name) || "Accept".equals(name);
            for (int i = 0; i < POOL; i++) {
                namePool[h][i] = http2 || decoderConstant ? sharedName : new AsciiString(name);
            }
            String fixed = headers[h][1];
            boolean varies = fixed == null || "unique".equals(values);
            for (int i = 0; i < POOL; i++) {
                String value = varies ? (fixed == null ? "v" : fixed) + "-" + h + "-" + i : fixed;
                // a fresh copy per slot, so a repeated value is equal but never the same instance
                value = new String(value.toCharArray());
                valuePool[h][i] = http2 ? new AsciiString(value) : value;
            }
        }
    }

    @Benchmark
    public HttpRequest mapBodilessRequest() {
        int slot = next++ & (POOL - 1);
        FullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/items", Unpooled.EMPTY_BUFFER);
        HttpHeaders headers = nettyRequest.headers();
        boolean reversed = reorder && (slot & 1) == 1;
        for (int i = 0; i < namePool.length; i++) {
            int h = reversed ? namePool.length - 1 - i : i;
            headers.add(namePool[h][slot], valuePool[h][slot]);
        }
        return mapper.mapFullHttpRequestToMockServerRequest(nettyRequest, null, null, null, requestProtocol);
    }
}
