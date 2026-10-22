package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.FileBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.MockServerCaTrustTestSupport;
import org.mockserver.test.Http2FlowControlBodies;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A matched expectation's FILE body is read on the server's file read pool, not on the event loop, and reaches the
 * wire exactly as the same content does as an inline STRING or BINARY body: over one HTTP/1.1 keep-alive connection
 * and on concurrent HTTP/2 streams larger than the flow-control window.
 */
public class FileBodyReadPoolIntegrationTest {

    private static final String PREVIOUS_WINDOW_SIZE;

    static {
        // the RFC default window, so an OVER_WINDOW body really crosses it (see Http2ThirdPartyClientConformanceIntegrationTest)
        PREVIOUS_WINDOW_SIZE = System.setProperty("jdk.httpclient.windowsize", "65535");
    }

    private static final String TEXT = "{\"greeting\":\"héllo ✓ 日本\"}";
    private static final MediaType TEXT_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType BINARY_TYPE = MediaType.parse("image/png");
    private static final int STREAMS = 6;

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static byte[] binary;

    @BeforeClass
    public static void startServer() throws Exception {
        File mocks = temporaryFolder.newFolder("mocks");
        File text = write(new File(mocks, "text.json"), TEXT.getBytes(StandardCharsets.UTF_8));
        binary = new byte[256 * 4];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i;
        }
        File image = write(new File(mocks, "image.png"), binary);
        File empty = write(new File(mocks, "empty.json"), new byte[0]);

        mockServer = new MockServer();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        mockServerClient.when(request("/file-text")).respond(response().withBody(new FileBody(text.getAbsolutePath(), TEXT_TYPE)));
        mockServerClient.when(request("/string-text")).respond(response().withBody(new StringBody(TEXT, TEXT_TYPE)));
        mockServerClient.when(request("/file-binary")).respond(response().withBody(new FileBody(image.getAbsolutePath(), BINARY_TYPE)));
        mockServerClient.when(request("/inline-binary")).respond(response().withBody(new BinaryBody(binary, BINARY_TYPE)));
        mockServerClient.when(request("/file-empty")).respond(response().withBody(new FileBody(empty.getAbsolutePath(), TEXT_TYPE)));
        mockServerClient.when(request("/file-missing")).respond(response().withBody(new FileBody(new File(mocks, "missing.json").getAbsolutePath(), TEXT_TYPE)));
        mockServerClient.when(request("/string-error")).respond(response().withStatusCode(500).withReasonPhrase("Internal Server Error").withBody("Error reading file for response body"));
        for (int i = 0; i < STREAMS; i++) {
            File large = write(new File(mocks, "large-" + i + ".txt"), largeBody(i).getBytes(StandardCharsets.UTF_8));
            mockServerClient.when(request("/large-" + i)).respond(response().withBody(new FileBody(large.getAbsolutePath(), MediaType.TEXT_PLAIN)));
        }
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (PREVIOUS_WINDOW_SIZE == null) {
            System.clearProperty("jdk.httpclient.windowsize");
        } else {
            System.setProperty("jdk.httpclient.windowsize", PREVIOUS_WINDOW_SIZE);
        }
    }

    @Test(timeout = 60000)
    public void shouldWriteFileBodiesAsInlineBodiesOnOneKeepAliveConnection() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            socket.setSoTimeout(10_000);
            String[][] pairs = {{"/file-text", "/string-text"}, {"/file-binary", "/inline-binary"}, {"/file-missing", "/string-error"}};
            for (String[] pair : pairs) {
                byte[] fromFile = exchange(socket, pair[0]);
                byte[] inline = exchange(socket, pair[1]);
                assertThat("response to " + pair[0] + " differs from " + pair[1], new String(fromFile, StandardCharsets.ISO_8859_1), is(new String(inline, StandardCharsets.ISO_8859_1)));
            }
            // an inline empty body loses its content type when sent to the server, so this one is pinned
            assertThat(new String(exchange(socket, "/file-empty"), StandardCharsets.ISO_8859_1),
                is("HTTP/1.1 200 OK\r\nconnection: keep-alive\r\ncontent-type: application/json; charset=utf-8\r\ncontent-length: 0\r\n\r\n"));
            assertThat(new String(exchange(socket, "/file-text"), StandardCharsets.UTF_8), startsWith("HTTP/1.1 200 OK"));
        }
        ThreadPoolExecutor fileReads = (ThreadPoolExecutor) mockServer.getScheduler().getFileReadExecutor();
        assertThat(fileReads, notNullValue());
        assertThat(fileReads.getCompletedTaskCount(), greaterThanOrEqualTo(5L));
    }

    @Test(timeout = 90000)
    public void shouldKeepConcurrentFileBodiesOnTheirOwnHttp2Streams() throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(MockServerCaTrustTestSupport.caTrustingSslContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        HttpResponse<String> warmUp = client.send(get("/large-0"), HttpResponse.BodyHandlers.ofString());
        assertThat(warmUp.version(), is(HttpClient.Version.HTTP_2));

        List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
        for (int i = 0; i < STREAMS; i++) {
            responses.add(client.sendAsync(get("/large-" + i), HttpResponse.BodyHandlers.ofString()));
        }
        CompletableFuture.allOf(responses.toArray(new CompletableFuture[0])).get(60, SECONDS);

        for (int i = 0; i < STREAMS; i++) {
            HttpResponse<String> response = responses.get(i).get();
            assertThat(response.version(), is(HttpClient.Version.HTTP_2));
            assertThat(response.statusCode(), is(200));
            assertThat("body of stream " + i + " was mis-routed or truncated", response.body(), is(largeBody(i)));
        }
    }

    @Test(timeout = 60000)
    public void shouldNotCreateFileReadPoolWhenNoFileBodyIsServed() throws Exception {
        MockServer withoutFiles = new MockServer();
        MockServerClient client = new MockServerClient("localhost", withoutFiles.getLocalPort());
        try {
            client.when(request("/inline")).respond(response("inline"));
            try (Socket socket = new Socket("localhost", withoutFiles.getLocalPort())) {
                socket.setSoTimeout(10_000);
                assertThat(new String(exchange(socket, "/inline"), StandardCharsets.UTF_8), startsWith("HTTP/1.1 200 OK"));
            }
            assertThat(withoutFiles.getScheduler().getFileReadExecutor(), nullValue());
        } finally {
            stopQuietly(client);
            stopQuietly(withoutFiles);
        }
    }

    private static HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + mockServer.getLocalPort() + path))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
    }

    private static String largeBody(int stream) {
        return Http2FlowControlBodies.body(Http2FlowControlBodies.Size.OVER_WINDOW, "file-stream-" + stream);
    }

    // one request and its whole response, read by Content-Length so the connection stays open for the next
    private static byte[] exchange(Socket socket, String path) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        while (!endsWithBlankLine(received)) {
            int next = input.read();
            if (next < 0) {
                throw new IOException("connection closed before the headers of " + path + " ended");
            }
            received.write(next);
        }
        int contentLength = 0;
        for (String line : received.toString(StandardCharsets.ISO_8859_1).split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        received.write(input.readNBytes(contentLength));
        return received.toByteArray();
    }

    private static boolean endsWithBlankLine(ByteArrayOutputStream received) {
        byte[] bytes = received.toByteArray();
        int length = bytes.length;
        return length >= 4 && bytes[length - 4] == '\r' && bytes[length - 3] == '\n' && bytes[length - 2] == '\r' && bytes[length - 1] == '\n';
    }

    private static File write(File file, byte[] content) throws IOException {
        Files.write(file.toPath(), content);
        return file;
    }
}
