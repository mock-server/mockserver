package org.mockserver.netty.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Protocol;
import org.mockserver.model.SegmentedBytes;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.mockserver.model.NottableString.string;

/**
 * Converts between HTTP/3 frames and MockServer's {@link HttpRequest}/{@link HttpResponse} model.
 * <p>
 * These are pure conversion helpers with no Netty channel dependencies, so they
 * can be unit-tested without the native QUIC transport.
 */
public final class Http3RequestBridge {

    // stateless; the same decoder HTTP/1.1 and HTTP/2 use, so every protocol builds the same body
    private static final BodyDecoderEncoder BODY_DECODER = new BodyDecoderEncoder();

    private Http3RequestBridge() {
        // utility class
    }

    /**
     * Build a MockServer {@link HttpRequest} from the HTTP/3 pseudo-headers and
     * accumulated body bytes.
     *
     * @param method    the :method pseudo-header value
     * @param path      the :path pseudo-header value (may include query string)
     * @param scheme    the :scheme pseudo-header value (nullable)
     * @param authority the :authority pseudo-header value (nullable)
     * @param headers   list of non-pseudo-header name/value pairs
     * @param body      the accumulated request body bytes (may be empty)
     * @return a fully populated HttpRequest
     */
    public static HttpRequest toHttpRequest(
        String method,
        String path,
        String scheme,
        String authority,
        List<Map.Entry<String, String>> headers,
        byte[] body
    ) {
        HttpRequest request = buildRequestWithoutBody(method, path, scheme, authority, headers);
        if (body != null && body.length > 0) {
            request.withBody(BODY_DECODER.bytesToBody(body, findContentType(headers)));
        }
        return request;
    }

    /**
     * As {@link #toHttpRequest(String, String, String, String, List, byte[])}, but reads the
     * accumulated request body from the {@link ByteBuf} it was accumulated into, copying it once.
     * <p>
     * The body is decoded by {@link BodyDecoderEncoder#bytesToBody}, exactly as the HTTP/1.1 and
     * HTTP/2 mappers do, so it keeps the wire bytes as its raw bytes and gets the same body type and
     * charset for every {@code Content-Type}.
     * <p>
     * <b>Buffer ownership:</b> the body is read non-destructively (reader index is not advanced) and
     * the buffer is <b>not</b> released here — the caller retains ownership and must release it (the
     * production handler does so in a {@code finally}, on every path including errors).
     */
    public static HttpRequest toHttpRequest(
        String method,
        String path,
        String scheme,
        String authority,
        List<Map.Entry<String, String>> headers,
        ByteBuf body
    ) {
        HttpRequest request = buildRequestWithoutBody(method, path, scheme, authority, headers);

        if (body != null && body.isReadable()) {
            byte[] bytes = new byte[body.readableBytes()];
            body.getBytes(body.readerIndex(), bytes);
            request.withBody(BODY_DECODER.bytesToBody(bytes, findContentType(headers)));
        }

        return request;
    }

    /**
     * As {@link #toHttpRequest(String, String, String, String, List, ByteBuf)}, unless {@code decompressedBody} (the
     * body {@link Http3RequestDecompressor} decompressed) is non-null: the request is then the one HTTP/1.1's
     * decompressor, aggregator and request mapper build. The body is the decompressed bytes and the wire bytes are kept
     * as its original body (when they differ); a {@code content-length} is replaced by the decompressed size, and
     * {@code content-encoding} is kept but moved after the other headers. Neither buffer is released here.
     */
    public static HttpRequest toHttpRequest(
        String method,
        String path,
        String scheme,
        String authority,
        List<Map.Entry<String, String>> headers,
        ByteBuf body,
        ByteBuf decompressedBody
    ) {
        if (decompressedBody == null) {
            return toHttpRequest(method, path, scheme, authority, headers, body);
        }
        HttpRequest request = buildRequestWithoutBody(method, path, scheme, authority, headersAfterDecompression(headers, decompressedBody.readableBytes()));
        byte[] decompressed = null;
        if (decompressedBody.isReadable()) {
            decompressed = new byte[decompressedBody.readableBytes()];
            decompressedBody.getBytes(decompressedBody.readerIndex(), decompressed);
            request.withBody(BODY_DECODER.bytesToBody(decompressed, findContentType(headers)));
        }
        if (body != null && body.isReadable()) {
            byte[] original = new byte[body.readableBytes()];
            body.getBytes(body.readerIndex(), original);
            if (!Arrays.equals(original, decompressed)) {
                request.withOriginalBody(original);
            }
        }
        return request;
    }

    /**
     * HTTP/1.1's decompressor drops {@code content-length}, its aggregator appends one with the decompressed size, and
     * its request mapper appends the {@code content-encoding} the decompressor removed, so both end up last, in that
     * order. A request with no {@code content-length} (on HTTP/1.1, a chunked one) gets none.
     */
    static List<Map.Entry<String, String>> headersAfterDecompression(List<Map.Entry<String, String>> headers, int decompressedLength) {
        List<Map.Entry<String, String>> rewritten = new ArrayList<>(headers.size() + 1);
        List<Map.Entry<String, String>> contentEncodings = new ArrayList<>(1);
        boolean hadContentLength = false;
        for (Map.Entry<String, String> header : headers) {
            if (CONTENT_LENGTH.equalsIgnoreCase(header.getKey())) {
                hadContentLength = true;
            } else if (CONTENT_ENCODING.equalsIgnoreCase(header.getKey())) {
                contentEncodings.add(header);
            } else {
                rewritten.add(header);
            }
        }
        if (hadContentLength) {
            rewritten.add(new AbstractMap.SimpleImmutableEntry<>(CONTENT_LENGTH, String.valueOf(decompressedLength)));
        }
        rewritten.addAll(contentEncodings);
        return rewritten;
    }

    /**
     * Build the {@link HttpRequest} with method, path, query string and headers set, but no body.
     * Shared by the {@code toHttpRequest} overloads so the body-materialisation strategies
     * differ only in how they read the body, never in how the rest of the request is built.
     */
    private static HttpRequest buildRequestWithoutBody(
        String method,
        String path,
        String scheme,
        String authority,
        List<Map.Entry<String, String>> headers
    ) {
        // split path and query
        String requestPath = path;
        String queryString = "";
        if (path != null) {
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                requestPath = path.substring(0, queryIndex);
                queryString = path.substring(queryIndex + 1);
            }
        }
        if (requestPath == null || requestPath.isEmpty()) {
            requestPath = "/";
        }

        HttpRequest request = HttpRequest.request()
            .withMethod(method != null ? method : "GET")
            .withPath(requestPath)
            .withSecure(true) // HTTP/3 is always over TLS
            // the HTTP/3 ALPN identifier is always "h3", so the negotiated protocol is
            // server-trusted and cannot be spoofed by a header (unlike the h2c upgrade);
            // tag the request so it can be matched on / verified by protocol
            .withProtocol(Protocol.HTTP_3);

        if (!queryString.isEmpty()) {
            request.withQueryStringParameters(parseQueryString(queryString));
        }

        // Names and values are built as literal NottableStrings (not parsed for a leading "!" or
        // "?") because this is an actual received request, never a matcher — the same convention as
        // FullHttpRequestToMockServerHttpRequest. Without it a received header value such as
        // "!literal" would be read as a negation and silently invert its own matching.
        // set authority as Host header if present
        if (authority != null && !authority.isEmpty()) {
            request.withHeader(string("host", false), string(authority, false));
        }

        // add regular headers
        if (headers != null) {
            for (Map.Entry<String, String> header : headers) {
                request.withHeader(
                    string(header.getKey(), false),
                    string(header.getValue() != null ? header.getValue() : "", false));
            }
        }

        return request;
    }

    /**
     * Find the {@code content-type} header value (case-insensitive), or null when absent.
     */
    private static String findContentType(List<Map.Entry<String, String>> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> header : headers) {
                if ("content-type".equalsIgnoreCase(header.getKey())) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extract pseudo-headers and regular headers from an HTTP/3 headers frame.
     */
    public static ParsedHeaders parseHeaders(Http3HeadersFrame headersFrame) {
        Http3Headers h3Headers = headersFrame.headers();
        String method = charSeqToString(h3Headers.method());
        String path = charSeqToString(h3Headers.path());
        String scheme = charSeqToString(h3Headers.scheme());
        String authority = charSeqToString(h3Headers.authority());

        List<Map.Entry<String, String>> regularHeaders = new ArrayList<>();
        boolean contentEncoded = false;
        for (Map.Entry<CharSequence, CharSequence> entry : h3Headers) {
            String name = entry.getKey().toString();
            // skip pseudo-headers (they start with ':')
            if (!name.startsWith(":")) {
                regularHeaders.add(new AbstractMap.SimpleImmutableEntry<>(name, entry.getValue().toString()));
                contentEncoded |= CONTENT_ENCODING.equalsIgnoreCase(name);
            }
        }

        return new ParsedHeaders(method, path, scheme, authority, regularHeaders, contentEncoded);
    }

    /**
     * Convert a MockServer {@link HttpResponse} into an HTTP/3 headers frame.
     */
    public static DefaultHttp3HeadersFrame toHttp3HeadersFrame(HttpResponse response) {
        return toHttp3HeadersFrame(response, false);
    }

    /**
     * As {@link #toHttp3HeadersFrame(HttpResponse)}, additionally dropping {@code content-length}
     * when {@code streaming} is true. A streamed response's length is not known when the headers are
     * sent, and a {@code content-length} copied from (for example) a relayed upstream response
     * describes a different body than the one actually streamed - which a conforming client treats
     * as a malformed message rather than merely ignoring.
     */
    public static DefaultHttp3HeadersFrame toHttp3HeadersFrame(HttpResponse response, boolean streaming) {
        DefaultHttp3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        int statusCode = response.getStatusCode() != null ? response.getStatusCode() : 200;
        headersFrame.headers().status(String.valueOf(statusCode));
        headersFrame.headers().add("server", "mockserver-http3");

        if (response.getHeaderMultimap() != null) {
            response.getHeaderMultimap().entries().forEach(entry -> {
                // Locale.ROOT: under a Turkish default locale "CONNECTION" folds to "connectıon"
                // and the forbidden-header filter below is silently bypassed entirely
                String name = entry.getKey().getValue().toLowerCase(Locale.ROOT);
                if (isForbiddenHttp3ResponseHeader(name, entry.getValue().getValue())
                    || (streaming && CONTENT_LENGTH.equals(name))) {
                    return;
                }
                headersFrame.headers().add(name, entry.getValue().getValue());
            });
        }

        return headersFrame;
    }

    private static final String CONTENT_LENGTH = "content-length";
    private static final String CONTENT_ENCODING = "content-encoding";

    /**
     * Whether a header field is one HTTP/3 forbids on a response.
     * <p>
     * RFC 9114 section 4.2 bans the connection-specific fields {@code Connection}, {@code Keep-Alive},
     * {@code Proxy-Connection}, {@code Transfer-Encoding} and {@code Upgrade} outright, and permits
     * {@code TE} only with the exact value {@code trailers}. A receiver "MUST treat" a message
     * carrying any of them "as malformed", so letting one through does not merely add a useless
     * header - it can make a conforming client reject the whole response.
     * <p>
     * This previously filtered only {@code connection} and {@code transfer-encoding}. The other
     * three reach a model {@link HttpResponse} two ways: an expectation can set any header
     * explicitly, and — more importantly — in proxy/forward mode
     * {@code FullHttpResponseToMockServerHttpResponse} copies an upstream response's headers onto
     * the model wholesale, stripping only the HTTP/2 extension headers. An HTTP/1.1 origin commonly
     * answers with {@code Keep-Alive: timeout=5, max=100}, which was therefore relayed verbatim
     * onto an HTTP/3 response.
     */
    static boolean isForbiddenHttp3ResponseHeader(String lowerCaseName, String value) {
        switch (lowerCaseName) {
            case "connection":
            case "keep-alive":
            case "proxy-connection":
            case "transfer-encoding":
            case "upgrade":
                return true;
            case "te":
                // TE is allowed, but only with the single value "trailers"
                return !"trailers".equalsIgnoreCase(value);
            default:
                return false;
        }
    }

    /**
     * Build a trailing HTTP/3 HEADERS frame from the response trailers, or null when the
     * response carries no trailers. The trailer field names are lower-cased per HTTP/3
     * (HTTP/2-style) header conventions. This is the general-purpose (non-gRPC) trailer
     * frame; gRPC trailers (grpc-status / grpc-message) are emitted separately by
     * {@code Http3GrpcResponseWriter}.
     */
    public static DefaultHttp3HeadersFrame toHttp3TrailersFrame(HttpResponse response) {
        if (response.getTrailerMultimap() == null || response.getTrailerMultimap().isEmpty()) {
            return null;
        }
        DefaultHttp3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();
        response.getTrailerMultimap().entries().forEach(entry ->
            // Locale.ROOT: a locale-sensitive fold would emit a non-ASCII trailer field name,
            // which DefaultHttp3Headers rejects as malformed.
            trailersFrame.headers().add(entry.getKey().getValue().toLowerCase(Locale.ROOT), entry.getValue().getValue())
        );
        return trailersFrame;
    }

    /**
     * Convert the body of a MockServer {@link HttpResponse} into an HTTP/3 data frame.
     * Returns null if the response has no body.
     */
    public static DefaultHttp3DataFrame toHttp3DataFrame(HttpResponse response) {
        SegmentedBytes segmentedBytes = BodyDecoderEncoder.wireSegments(response.getBody());
        if (segmentedBytes != null) {
            return segmentedBytes.size() == 0 ? null : new DefaultHttp3DataFrame(BodyDecoderEncoder.segmentsToByteBuf(segmentedBytes));
        }
        byte[] bodyBytes = response.getBodyAsRawBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }
        return new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(bodyBytes));
    }

    static final int BLOCK_BYTES = 16 * 1024;
    static final int COALESCE_AFTER_COMPONENTS = 64;

    /**
     * Accumulate body data from an HTTP/3 data frame into a composite buffer. HTTP/3 hands a body over in pieces of
     * about one QUIC packet (1.1 KiB) whatever DATA frame size the client sent, so once the body has 64 components,
     * pieces under 16 KiB are copied into 16 KiB blocks rather than kept one component each: each byte is copied at
     * most once, and a body of one-byte pieces needs one component per 16 KiB.
     */
    public static void accumulateBody(CompositeByteBuf composite, Http3DataFrame dataFrame) {
        accumulateBody(composite, dataFrame.content());
    }

    /**
     * As {@link #accumulateBody(CompositeByteBuf, Http3DataFrame)} for one piece of a body; {@code content} is retained
     * when it is added as a component, so the caller keeps its own reference.
     */
    static void accumulateBody(CompositeByteBuf composite, ByteBuf content) {
        int length = content.readableBytes();
        if (length == 0) {
            return;
        }
        if (length < BLOCK_BYTES && composite.numComponents() >= COALESCE_AFTER_COMPONENTS) {
            if (composite.writableBytes() < length) {
                composite.capacity(composite.capacity() + BLOCK_BYTES);
            }
            composite.writeBytes(content, content.readerIndex(), length);
        } else {
            // a new component starts at the composite's capacity, so drop the unwritten end of the last block first
            composite.capacity(composite.writerIndex());
            composite.addComponent(true, content.retain());
        }
    }

    /**
     * Keeps {@code composite} under {@code componentLimit} components when {@link #accumulateBody} alone does not,
     * which takes pieces alternating between small and 16 KiB or more. {@link CompositeByteBuf}'s own consolidation
     * copies the whole body each time; this copies only the components after the leading {@code merged} ones into one.
     * The whole-body copy once half the limit is merged blocks needs a component limit far smaller than the body
     * bound it is derived from, which {@code maxRequestBodySize} cannot produce.
     *
     * @return how many leading components are now merged blocks, to pass back on the next call
     */
    public static int limitComponents(CompositeByteBuf composite, int componentLimit, int merged) {
        int components = composite.numComponents();
        if (components < componentLimit) {
            return merged;
        }
        if (merged >= componentLimit / 2) {
            composite.consolidate();
            return 1;
        }
        composite.consolidate(merged, components - merged);
        return merged + 1;
    }

    /**
     * Read the accumulated composite buffer into a byte array.
     */
    public static byte[] readAccumulatedBody(CompositeByteBuf composite) {
        if (composite.readableBytes() == 0) {
            return new byte[0];
        }
        byte[] body = new byte[composite.readableBytes()];
        composite.readBytes(body);
        return body;
    }

    private static String charSeqToString(CharSequence seq) {
        return seq != null ? seq.toString() : null;
    }

    private static org.mockserver.model.Parameters parseQueryString(String queryString) {
        org.mockserver.model.Parameters parameters = new org.mockserver.model.Parameters();
        if (queryString == null || queryString.isEmpty()) {
            return parameters;
        }
        for (String param : queryString.split("&")) {
            int eqIndex = param.indexOf('=');
            if (eqIndex >= 0) {
                String name = param.substring(0, eqIndex);
                String value = param.substring(eqIndex + 1);
                parameters.withEntry(name, value);
            } else if (!param.isEmpty()) {
                parameters.withEntry(param, "");
            }
        }
        return parameters;
    }

    /**
     * Parsed HTTP/3 pseudo-headers and regular headers.
     */
    public static final class ParsedHeaders {
        private final String method;
        private final String path;
        private final String scheme;
        private final String authority;
        private final List<Map.Entry<String, String>> headers;
        private final boolean contentEncoded;

        public ParsedHeaders(String method, String path, String scheme, String authority, List<Map.Entry<String, String>> headers, boolean contentEncoded) {
            this.method = method;
            this.path = path;
            this.scheme = scheme;
            this.authority = authority;
            this.headers = headers;
            this.contentEncoded = contentEncoded;
        }

        /**
         * Whether the request has a {@code content-encoding} header.
         */
        public boolean contentEncoded() {
            return contentEncoded;
        }

        public String method() {
            return method;
        }

        public String path() {
            return path;
        }

        public String scheme() {
            return scheme;
        }

        public String authority() {
            return authority;
        }

        public List<Map.Entry<String, String>> headers() {
            return headers;
        }
    }
}
