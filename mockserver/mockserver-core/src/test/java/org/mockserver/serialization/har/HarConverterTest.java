package org.mockserver.serialization.har;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class HarConverterTest {

    private final HarConverter harConverter = new HarConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldConvertSimpleGetRequest() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/test")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
                    .withSecure(false)
            )
            .withHttpResponse(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
                    .withBody("hello")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode log = root.get("log");
        assertThat(log.get("version").asText(), is("1.2"));
        assertThat(log.get("creator").get("name").asText(), is("MockServer"));

        JsonNode harEntry = log.get("entries").get(0);
        assertThat(harEntry.get("startedDateTime").asText(), is("2026-01-15T10:30:00.000Z"));

        JsonNode harRequest = harEntry.get("request");
        assertThat(harRequest.get("method").asText(), is("GET"));
        assertThat(harRequest.get("url").asText(), is("http://example.com/api/test"));
        assertThat(harRequest.get("httpVersion").asText(), is("HTTP/1.1"));

        JsonNode harResponse = harEntry.get("response");
        assertThat(harResponse.get("status").asInt(), is(200));
        assertThat(harResponse.get("statusText").asText(), is("OK"));
        assertThat(harResponse.get("content").get("text").asText(), is("hello"));
    }

    @Test
    public void shouldConvertPostRequestWithJsonBody() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/data")
                    .withMethod("POST")
                    .withHeader("host", "example.com")
                    .withHeader("content-type", "application/json")
                    .withBody("{\"key\":\"value\"}")
                    .withSecure(true)
            )
            .withHttpResponse(
                response()
                    .withStatusCode(201)
                    .withReasonPhrase("Created")
                    .withBody("{\"id\":1}")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harRequest = root.get("log").get("entries").get(0).get("request");
        assertThat(harRequest.get("method").asText(), is("POST"));
        assertThat(harRequest.get("url").asText(), is("https://example.com/api/data"));
        assertThat(harRequest.get("postData").get("text").asText(), is("{\"key\":\"value\"}"));
    }

    @Test
    public void shouldConvertBinaryResponseBody() throws Exception {
        // given
        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, 0x03};
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/file")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
            )
            .withHttpResponse(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
                    .withBody(binary(binaryData, MediaType.parse("application/octet-stream")))
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode content = root.get("log").get("entries").get(0).get("response").get("content");
        assertThat(content.get("encoding").asText(), is("base64"));
        assertThat(content.get("text").asText(), is(Base64.getEncoder().encodeToString(binaryData)));
        assertThat(content.get("size").asLong(), is(4L));
        assertThat(content.get("mimeType").asText(), is("application/octet-stream"));
    }

    @Test
    public void shouldReconstructAbsoluteUrl() {
        // given - with socket address
        HttpRequest requestWithSocket = request("/path")
            .withSocketAddress("api.example.com", 8443, SocketAddress.Scheme.HTTPS);

        // when
        String url = harConverter.reconstructUrl(requestWithSocket);

        // then
        assertThat(url, is("https://api.example.com:8443/path"));
    }

    @Test
    public void shouldReconstructUrlFromHostHeader() {
        // given
        HttpRequest httpRequest = request("/path")
            .withHeader("host", "example.com:9090")
            .withSecure(false);

        // when
        String url = harConverter.reconstructUrl(httpRequest);

        // then
        assertThat(url, is("http://example.com:9090/path"));
    }

    @Test
    public void shouldReconstructUrlWithDefaultPort() {
        // given
        HttpRequest httpRequest = request("/path")
            .withHeader("host", "example.com")
            .withSecure(false);

        // when
        String url = harConverter.reconstructUrl(httpRequest);

        // then
        assertThat(url, is("http://example.com/path"));
    }

    @Test
    public void shouldReconstructUrlWithDefaultHttpsPort() {
        // given
        HttpRequest httpRequest = request("/path")
            .withHeader("host", "example.com")
            .withSecure(true);

        // when
        String url = harConverter.reconstructUrl(httpRequest);

        // then
        assertThat(url, is("https://example.com/path"));
    }

    @Test
    public void shouldReconstructUrlWithQueryParameters() {
        // given
        HttpRequest httpRequest = request("/search")
            .withHeader("host", "example.com")
            .withQueryStringParameter("q", "test")
            .withQueryStringParameter("page", "1");

        // when
        String url = harConverter.reconstructUrl(httpRequest);

        // then
        assertThat(url, is("http://example.com/search?q=test&page=1"));
    }

    @Test
    public void shouldUrlEncodeQueryParameterSpecialCharacters() {
        // given
        HttpRequest httpRequest = request("/search")
            .withHeader("host", "example.com")
            .withQueryStringParameter("q", "hello world")
            .withQueryStringParameter("filter", "a=b&c=d");

        // when
        String url = harConverter.reconstructUrl(httpRequest);

        // then
        assertThat(url, is("http://example.com/search?q=hello+world&filter=a%3Db%26c%3Dd"));
    }

    @Test
    public void shouldHandleHttp2Protocol() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/test")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
                    .withProtocol(Protocol.HTTP_2)
                    .withStreamId(3)
            )
            .withHttpResponse(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harEntry = root.get("log").get("entries").get(0);
        assertThat(harEntry.get("request").get("httpVersion").asText(), is("HTTP/2"));
        assertThat(harEntry.get("connection").asText(), is("3"));
    }

    @Test
    public void shouldHandleEmptyEntries() throws Exception {
        // when
        String har = harConverter.serialize(Collections.emptyList());

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode log = root.get("log");
        assertThat(log.get("version").asText(), is("1.2"));
        assertThat(log.get("entries").size(), is(0));
    }

    @Test
    public void shouldConvertMultipleEntries() throws Exception {
        // given
        List<LogEventRequestAndResponse> entries = Arrays.asList(
            new LogEventRequestAndResponse()
                .withTimestamp("2026-01-15T10:30:00.000Z")
                .withHttpRequest(request("/first").withMethod("GET").withHeader("host", "example.com"))
                .withHttpResponse(response().withStatusCode(200).withReasonPhrase("OK")),
            new LogEventRequestAndResponse()
                .withTimestamp("2026-01-15T10:30:01.000Z")
                .withHttpRequest(request("/second").withMethod("POST").withHeader("host", "example.com"))
                .withHttpResponse(response().withStatusCode(201).withReasonPhrase("Created"))
        );

        // when
        String har = harConverter.serialize(entries);

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harEntries = root.get("log").get("entries");
        assertThat(harEntries.size(), is(2));
        assertThat(harEntries.get(0).get("request").get("url").asText(), is("http://example.com/first"));
        assertThat(harEntries.get(1).get("request").get("url").asText(), is("http://example.com/second"));
    }

    @Test
    public void shouldConvertHeadersWithMultipleValues() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/test")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
                    .withHeader("accept", "text/html", "application/json")
            )
            .withHttpResponse(
                response().withStatusCode(200).withReasonPhrase("OK")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode headers = root.get("log").get("entries").get(0).get("request").get("headers");
        int acceptCount = 0;
        for (JsonNode h : headers) {
            if ("accept".equals(h.get("name").asText())) {
                acceptCount++;
            }
        }
        assertThat(acceptCount, is(2));
    }

    @Test
    public void shouldConvertCookies() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/test")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
                    .withCookie("session", "abc123")
            )
            .withHttpResponse(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
                    .withCookie("token", "xyz789")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode requestCookies = root.get("log").get("entries").get(0).get("request").get("cookies");
        assertThat(requestCookies.size(), is(1));
        assertThat(requestCookies.get(0).get("name").asText(), is("session"));
        assertThat(requestCookies.get(0).get("value").asText(), is("abc123"));

        JsonNode responseCookies = root.get("log").get("entries").get(0).get("response").get("cookies");
        assertThat(responseCookies.size(), is(1));
        assertThat(responseCookies.get(0).get("name").asText(), is("token"));
        assertThat(responseCookies.get(0).get("value").asText(), is("xyz789"));
    }

    @Test
    public void shouldConvertParameterBody() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/api/form")
                    .withMethod("POST")
                    .withHeader("host", "example.com")
                    .withBody(params(
                        param("username", "admin"),
                        param("password", "secret")
                    ))
            )
            .withHttpResponse(
                response().withStatusCode(200).withReasonPhrase("OK")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode postData = root.get("log").get("entries").get(0).get("request").get("postData");
        JsonNode params = postData.get("params");
        assertThat(params.size(), is(2));
        assertThat(params.get(0).get("name").asText(), is("username"));
        assertThat(params.get(0).get("value").asText(), is("admin"));
        assertThat(params.get(1).get("name").asText(), is("password"));
        assertThat(params.get(1).get("value").asText(), is("secret"));
    }

    @Test
    public void shouldConvertRedirectResponse() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/old-path")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
            )
            .withHttpResponse(
                response()
                    .withStatusCode(302)
                    .withReasonPhrase("Found")
                    .withHeader("Location", "https://example.com/new-path")
            );

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harResponse = root.get("log").get("entries").get(0).get("response");
        assertThat(harResponse.get("redirectURL").asText(), is("https://example.com/new-path"));
    }

    @Test
    public void shouldHandleNullRequestAndResponse() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z");

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harEntry = root.get("log").get("entries").get(0);
        assertThat(harEntry.get("request").get("method").asText(), is(""));
        assertThat(harEntry.get("response").get("status").asInt(), is(0));
    }

    @Test
    public void shouldIncludeTimingsWithZeroValues() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(request("/test").withHeader("host", "example.com"))
            .withHttpResponse(response().withStatusCode(200).withReasonPhrase("OK"));

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode timings = root.get("log").get("entries").get(0).get("timings");
        assertThat(timings.get("send").asLong(), is(0L));
        assertThat(timings.get("wait").asLong(), is(0L));
        assertThat(timings.get("receive").asLong(), is(0L));
        assertThat(timings.get("blocked").asLong(), is(-1L));
        assertThat(timings.get("dns").asLong(), is(-1L));
        assertThat(timings.get("connect").asLong(), is(-1L));
        assertThat(timings.get("ssl").asLong(), is(-1L));
    }

    @Test
    public void shouldIncludeEmptyCache() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(request("/test").withHeader("host", "example.com"))
            .withHttpResponse(response().withStatusCode(200).withReasonPhrase("OK"));

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode cache = root.get("log").get("entries").get(0).get("cache");
        assertThat(cache, is(notNullValue()));
        assertThat(cache.size(), is(0));
    }

    @Test
    public void shouldSetServerIPAddressFromRemoteAddress() throws Exception {
        // given
        LogEventRequestAndResponse entry = new LogEventRequestAndResponse()
            .withTimestamp("2026-01-15T10:30:00.000Z")
            .withHttpRequest(
                request("/test")
                    .withMethod("GET")
                    .withHeader("host", "example.com")
                    .withRemoteAddress("192.168.1.100:8080")
            )
            .withHttpResponse(response().withStatusCode(200).withReasonPhrase("OK"));

        // when
        String har = harConverter.serialize(Collections.singletonList(entry));

        // then
        JsonNode root = objectMapper.readTree(har);
        JsonNode harEntry = root.get("log").get("entries").get(0);
        assertThat(harEntry.get("serverIPAddress").asText(), is("192.168.1.100"));
    }
}
