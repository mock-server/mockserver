package org.mockserver.netty.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.Body;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.client.RecordedExpectationsOptions.recordedExpectationsOptions;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Records real traffic through a MockServer proxy, retrieves it as expectations with the recording options
 * ({@code repeatsAsScenarios}, {@code captureHeaders}, {@code extractBodiesLargerThan}) and replays those
 * expectations from a separate MockServer, asserting the replay serves what the upstream served.
 */
public class RecordedExpectationOptionsProxyIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockServer upstream;
    private MockServer proxy;
    private MockServer replay;
    private File bodiesDirectory;

    @Before
    public void startServers() {
        bodiesDirectory = new File(temporaryFolder.getRoot(), "recorded-bodies");
        upstream = new MockServer(configuration(), 0);
        proxy = new MockServer(configuration().recordedBodyFilesDirectory(bodiesDirectory.getPath()), 0);
        replay = new MockServer(configuration(), 0);
    }

    @After
    public void stopServers() {
        for (MockServer server : new MockServer[]{replay, proxy, upstream}) {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void shouldReplayARequestRecordedWithChangingResponsesInOrder() throws IOException {
        MockServerClient upstreamClient = new MockServerClient("127.0.0.1", upstream.getLocalPort());
        upstreamClient.when(request("/status"), Times.once()).respond(response("pending"));
        upstreamClient.when(request("/status"), Times.once()).respond(response("running"));
        upstreamClient.when(request("/status")).respond(response("done"));
        List<String> recorded = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            recorded.add(body(proxied("/status", "User-Agent: client")));
        }
        assertThat(recorded, contains("pending", "running", "done", "done"));
        MockServerClient proxyClient = new MockServerClient("127.0.0.1", proxy.getLocalPort());

        assertThat(proxyClient.retrieveRecordedExpectations(request("/status")).length, is(4));
        Expectation[] sequenced = proxyClient.retrieveRecordedExpectations(request("/status"), recordedExpectationsOptions().withRepeatsAsScenarios(true));

        assertThat(sequenced.length, is(1));
        assertThat(sequenced[0].getResponseMode(), is(ResponseMode.SWITCH));
        new MockServerClient("127.0.0.1", replay.getLocalPort()).upsert(sequenced);
        List<String> replayed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            replayed.add(body(asRecorded(replay.getLocalPort(), "/status", "User-Agent: client")));
        }
        assertThat(replayed, contains("pending", "running", "done", "done", "done"));
    }

    @Test
    public void shouldKeepRepeatsThatDifferInAHeaderApartUnlessOnlyCapturedHeadersAreCompared() throws IOException {
        MockServerClient upstreamClient = new MockServerClient("127.0.0.1", upstream.getLocalPort());
        upstreamClient.when(request("/me"), Times.once()).respond(response("first"));
        upstreamClient.when(request("/me")).respond(response("second"));
        body(proxied("/me", "Authorization: Bearer alice\r\nX-Tenant: acme"));
        body(proxied("/me", "Authorization: Bearer bob\r\nX-Tenant: acme"));
        MockServerClient proxyClient = new MockServerClient("127.0.0.1", proxy.getLocalPort());

        Expectation[] perUser = proxyClient.retrieveRecordedExpectations(request("/me"), recordedExpectationsOptions().withRepeatsAsScenarios(true));
        Expectation[] perTenant = proxyClient.retrieveRecordedExpectations(request("/me"), recordedExpectationsOptions().withRepeatsAsScenarios(true).withCaptureHeaders("X-Tenant"));

        assertThat(perUser.length, is(2));
        assertThat(((HttpRequest) perUser[0].getHttpRequest()).getFirstHeader("Authorization"), is("Bearer alice"));
        assertThat(((HttpRequest) perUser[1].getHttpRequest()).getFirstHeader("Authorization"), is("Bearer bob"));
        assertThat(perTenant.length, is(1));
        assertThat(perTenant[0].getHttpResponses().size(), is(2));
    }

    @Test
    public void shouldKeepOnlyTheCapturedRequestHeadersInTheMatcher() throws IOException {
        new MockServerClient("127.0.0.1", upstream.getLocalPort()).when(request("/tenant")).respond(response("ok"));
        body(proxied("/tenant", "X-Tenant: acme\r\nAccept: text/plain\r\nUser-Agent: recorder"));
        MockServerClient proxyClient = new MockServerClient("127.0.0.1", proxy.getLocalPort());

        HttpRequest withAllHeaders = (HttpRequest) proxyClient.retrieveRecordedExpectations(request("/tenant"))[0].getHttpRequest();
        HttpRequest captured = (HttpRequest) proxyClient.retrieveRecordedExpectations(request("/tenant"), recordedExpectationsOptions().withCaptureHeaders("x-tenant"))[0].getHttpRequest();
        HttpRequest none = (HttpRequest) proxyClient.retrieveRecordedExpectations(request("/tenant"), recordedExpectationsOptions().withCaptureHeaders())[0].getHttpRequest();

        assertThat(withAllHeaders.getFirstHeader("User-Agent"), is("recorder"));
        assertThat(captured.getHeaders().getEntries().stream().map(header -> header.getName().getValue()).collect(Collectors.toList()), contains("X-Tenant"));
        assertThat(captured.getFirstHeader("X-Tenant"), is("acme"));
        assertThat(none.getHeaders(), nullValue());
    }

    @Test
    public void shouldMoveLargeRecordedBodiesIntoFilesThatReplay() throws IOException {
        String large = "{\"items\":[" + "\"item\",".repeat(300) + "\"last\"]}";
        new MockServerClient("127.0.0.1", upstream.getLocalPort()).when(request("/report")).respond(response().withBody(large, MediaType.APPLICATION_JSON));
        new MockServerClient("127.0.0.1", upstream.getLocalPort()).when(request("/small")).respond(response("tiny"));
        assertThat(body(proxied("/report", "")), is(large));
        assertThat(body(proxied("/small", "")), is("tiny"));
        MockServerClient proxyClient = new MockServerClient("127.0.0.1", proxy.getLocalPort());

        Expectation[] extracted = proxyClient.retrieveRecordedExpectations(request(), recordedExpectationsOptions().withExtractBodiesLargerThan(1024L).withCaptureHeaders());

        Body<?> reportBody = responseFor(extracted, "/report").getHttpResponse().getBody();
        assertThat(reportBody.getType(), is(Body.Type.FILE));
        Path file = Path.of(((FileBody) reportBody).getFilePath());
        assertThat(file.getParent().toFile().getCanonicalPath(), is(bodiesDirectory.getCanonicalPath()));
        assertThat(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), is(large));
        assertThat(responseFor(extracted, "/small").getHttpResponse().getBodyAsString(), is("tiny"));
        new MockServerClient("127.0.0.1", replay.getLocalPort()).upsert(extracted);
        assertThat(body(direct(replay.getLocalPort(), "/report", "")), is(large));
    }

    @Test
    public void shouldRejectBodyExtractionWithoutAConfiguredDirectoryOrWithAnInvalidThreshold() throws IOException {
        String withoutDirectory = send(upstream.getLocalPort(), "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&extractBodiesLargerThan=1");
        String negative = send(proxy.getLocalPort(), "/mockserver/retrieve?type=RECORDED_EXPECTATIONS&extractBodiesLargerThan=-1");

        assertThat(withoutDirectory, containsString("400:"));
        assertThat(withoutDirectory, containsString("recordedBodyFilesDirectory"));
        assertThat(negative, containsString("400:"));
        assertThat(negative, containsString("extractBodiesLargerThan"));
    }

    private static Expectation responseFor(Expectation[] expectations, String path) {
        for (Expectation expectation : expectations) {
            if (path.equals(((HttpRequest) expectation.getHttpRequest()).getPath().getValue())) {
                return expectation;
            }
        }
        throw new AssertionError("no recorded expectation for " + path);
    }

    private String proxied(String path, String extraHeaders) throws IOException {
        String upstreamHost = "127.0.0.1:" + upstream.getLocalPort();
        return exchange(proxy.getLocalPort(), "GET http://" + upstreamHost + path + " HTTP/1.1\r\nHost: " + upstreamHost + "\r\n", extraHeaders);
    }

    private String asRecorded(int port, String path, String extraHeaders) throws IOException {
        return exchange(port, "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + upstream.getLocalPort() + "\r\n", extraHeaders);
    }

    private static String direct(int port, String path, String extraHeaders) throws IOException {
        return exchange(port, "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n", extraHeaders);
    }

    private static String exchange(int port, String requestLineAndHost, String extraHeaders) throws IOException {
        String head = requestLineAndHost + (extraHeaders.isEmpty() ? "" : extraHeaders + "\r\n") + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(20_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = new String(drain(socket.getInputStream()), StandardCharsets.UTF_8);
            assertThat("expected a 200 response:\n" + response, response, containsString(" 200 "));
            return response;
        }
    }

    private static String body(String rawResponse) {
        return rawResponse.substring(rawResponse.indexOf("\r\n\r\n") + 4);
    }

    private static String send(int port, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        try {
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return statusCode + ":" + (stream == null ? "" : new String(drain(stream), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
