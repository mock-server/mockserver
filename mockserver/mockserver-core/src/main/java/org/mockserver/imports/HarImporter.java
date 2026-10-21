package org.mockserver.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.filters.TransportHeaderFilter;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;
import org.mockserver.serialization.ObjectMapperFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Imports expectations from HAR (HTTP Archive) format JSON files.
 *
 * <p>For each entry in {@code log.entries[]}, builds a request matcher (method + path + query
 * string parameters) and a response (status, filtered headers, body). The generated matchers
 * are intentionally permissive: volatile headers (Date, Set-Cookie, etc.) are excluded from
 * request matching, and request body matching is only applied when {@code postData.text} is
 * present and reasonably sized.
 */
public class HarImporter {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    /**
     * Headers that should NOT appear in request matchers because they are volatile,
     * hop-by-hop, or would over-constrain matching.
     */
    private static final Set<String> VOLATILE_REQUEST_HEADERS = Set.of(
        "host", "date", "user-agent", "accept", "accept-encoding", "accept-language",
        "connection", "keep-alive", "upgrade", "transfer-encoding", "content-length",
        "cookie", "referer", "origin", "cache-control", "pragma", "if-none-match",
        "if-modified-since", "authorization", "proxy-authorization",
        "x-request-id", "x-correlation-id", "traceparent", "tracestate"
    );

    /**
     * The lower-cased set of request header names that are volatile, hop-by-hop or
     * over-constraining and so should not appear in a generated request matcher.
     * Exposed (read-only) so the recorded-expectation consolidation path can reuse
     * exactly the same set when stripping volatile headers from recorded requests.
     *
     * @return an immutable set of lower-cased volatile request header names
     */
    public static Set<String> volatileRequestHeaders() {
        return VOLATILE_REQUEST_HEADERS;
    }

    /**
     * Headers that should NOT appear in response bodies because they are hop-by-hop
     * or volatile.
     */
    private static final Set<String> VOLATILE_RESPONSE_HEADERS = Set.of(
        "date", "transfer-encoding", "content-length", "connection", "keep-alive",
        "server", "set-cookie", "age", "x-request-id", "x-correlation-id",
        "traceparent", "tracestate", "vary"
    );

    /**
     * The lower-cased set of response header names that are hop-by-hop or change on every response
     * (such as {@code Date}), so they are left out when deciding whether two recorded responses are the same.
     *
     * @return an immutable set of lower-cased volatile response header names
     */
    public static Set<String> volatileResponseHeaders() {
        return VOLATILE_RESPONSE_HEADERS;
    }

    /** Maximum request body size (in characters) to include in a matcher. */
    private static final int MAX_REQUEST_BODY_SIZE = 4096;

    /**
     * Parses a HAR JSON string and returns one expectation per entry, with
     * sensitive headers and body fields redacted (see {@link ImportRedaction}).
     * Captured HAR files routinely contain real {@code Authorization}/API-key
     * headers and secret body fields, so redaction is on by default.
     *
     * @param harJson the HAR document as a JSON string
     * @return the generated expectations (may be empty if there are no entries)
     * @throws IllegalArgumentException if the JSON is null, blank, or not a valid HAR
     */
    public List<Expectation> importExpectations(String harJson) {
        return importExpectations(harJson, ImportRedaction.Options.enabled());
    }

    /**
     * Parses a HAR JSON string and returns one expectation per entry, applying the
     * supplied redaction options before the expectations are returned.
     *
     * @param harJson          the HAR document as a JSON string
     * @param redactionOptions controls whether/how sensitive data is masked; pass
     *                         {@link ImportRedaction.Options#disabled()} to keep
     *                         imported values verbatim
     * @return the generated expectations (may be empty if there are no entries)
     * @throws IllegalArgumentException if the JSON is null, blank, or not a valid HAR
     */
    public List<Expectation> importExpectations(String harJson, ImportRedaction.Options redactionOptions) {
        if (harJson == null || harJson.trim().isEmpty()) {
            throw new IllegalArgumentException("HAR JSON body is required");
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(harJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse HAR JSON: " + e.getMessage(), e);
        }

        JsonNode log = root.path("log");
        if (log.isMissingNode()) {
            throw new IllegalArgumentException("not a valid HAR document — missing top-level 'log' object");
        }

        JsonNode entries = log.path("entries");
        if (entries.isMissingNode() || !entries.isArray()) {
            throw new IllegalArgumentException("not a valid HAR document — missing 'log.entries' array");
        }

        List<Expectation> expectations = new ArrayList<>();
        for (JsonNode entry : entries) {
            Expectation expectation = buildExpectation(entry);
            if (expectation != null) {
                expectations.add(expectation);
            }
        }
        return ImportIds.fromRequestMatchers(ImportRedaction.redact(expectations, redactionOptions));
    }

    private Expectation buildExpectation(JsonNode entry) {
        JsonNode reqNode = entry.path("request");
        JsonNode resNode = entry.path("response");
        if (reqNode.isMissingNode() || resNode.isMissingNode()) {
            return null;
        }

        // --- Build request matcher ---
        String method = textOrNull(reqNode, "method");
        String url = textOrNull(reqNode, "url");
        if (method == null || url == null) {
            return null;
        }

        String path;
        List<Parameter> queryParams;
        try {
            URI uri = new URI(url);
            path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            queryParams = parseQueryString(uri.getRawQuery());
        } catch (Exception e) {
            // Fall back: use url as path
            path = url.startsWith("/") ? url : "/";
            queryParams = List.of();
        }

        // Also parse query params from the HAR queryString array if present
        JsonNode harQueryString = reqNode.path("queryString");
        if (harQueryString.isArray() && !harQueryString.isEmpty()) {
            queryParams = parseHarParams(harQueryString);
        }

        var httpRequest = request()
            .withMethod(method.toUpperCase(Locale.ROOT))
            .withPath(path);

        if (!queryParams.isEmpty()) {
            httpRequest.withQueryStringParameters(queryParams);
        }

        // Request headers (only non-volatile, meaningful ones)
        List<Header> requestHeaders = filterHeaders(reqNode.path("headers"), VOLATILE_REQUEST_HEADERS);
        requestHeaders.removeIf(header -> TransportHeaderFilter.isTransportHeader(header.getName().getValue()));
        if (!requestHeaders.isEmpty()) {
            httpRequest.withHeaders(requestHeaders);
        }

        // Request body (only if postData.text is present and small)
        JsonNode postData = reqNode.path("postData");
        if (!postData.isMissingNode()) {
            String postText = textOrNull(postData, "text");
            if (postText != null && !postText.isEmpty() && postText.length() <= MAX_REQUEST_BODY_SIZE) {
                httpRequest.withBody(postText);
            }
        }

        // --- Build response ---
        int status = resNode.path("status").asInt(200);

        var httpResponse = response()
            .withStatusCode(status);

        // Response headers (filter volatile)
        List<Header> responseHeaders = filterHeaders(resNode.path("headers"), VOLATILE_RESPONSE_HEADERS);
        JsonNode content = resNode.path("content");
        String mimeType = textOrNull(content, "mimeType");
        boolean hasContentTypeHeader = responseHeaders.stream().anyMatch(header -> header.getName().getValue().equalsIgnoreCase("content-type"));
        if (!hasContentTypeHeader && mimeType != null && !mimeType.isBlank()) {
            responseHeaders.add(Header.header("Content-Type", mimeType));
        }
        if (!responseHeaders.isEmpty()) {
            httpResponse.withHeaders(responseHeaders);
        }

        // Response body
        if (!content.isMissingNode()) {
            String bodyText = textOrNull(content, "text");
            if (bodyText != null && !bodyText.isEmpty()) {
                String encoding = textOrNull(content, "encoding");
                if ("base64".equalsIgnoreCase(encoding)) {
                    try {
                        bodyText = new String(Base64.getDecoder().decode(bodyText));
                    } catch (IllegalArgumentException ignored) {
                        // leave as-is if base64 decode fails
                    }
                }
                httpResponse.withBody(bodyText);
            }
        }

        return new Expectation(httpRequest)
            .withId("har")
            .thenRespond(httpResponse);
    }

    private List<Parameter> parseQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return List.of();
        }
        List<Parameter> params = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String name = decodeComponent(pair.substring(0, eq));
                String value = decodeComponent(pair.substring(eq + 1));
                params.add(Parameter.param(name, value));
            } else {
                params.add(Parameter.param(decodeComponent(pair), ""));
            }
        }
        return params;
    }

    private List<Parameter> parseHarParams(JsonNode queryStringArray) {
        List<Parameter> params = new ArrayList<>();
        for (JsonNode param : queryStringArray) {
            String name = textOrNull(param, "name");
            String value = textOrNull(param, "value");
            if (name != null) {
                params.add(Parameter.param(name, value != null ? value : ""));
            }
        }
        return params;
    }

    private List<Header> filterHeaders(JsonNode headersArray, Set<String> volatileSet) {
        List<Header> headers = new ArrayList<>();
        if (headersArray == null || !headersArray.isArray()) {
            return headers;
        }
        for (JsonNode headerNode : headersArray) {
            String name = textOrNull(headerNode, "name");
            String value = textOrNull(headerNode, "value");
            if (name != null && value != null && !volatileSet.contains(name.toLowerCase(Locale.ROOT))) {
                headers.add(Header.header(name, value));
            }
        }
        return headers;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private static String decodeComponent(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
