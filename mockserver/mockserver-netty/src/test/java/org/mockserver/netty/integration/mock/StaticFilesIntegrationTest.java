package org.mockserver.netty.integration.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.verify.VerificationTimes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A server with {@code staticFilesDirectory} answers GET and HEAD requests that match no expectation and are not
 * proxied from that directory, sent over a raw socket so traversal paths reach the server exactly as written.
 */
public class StaticFilesIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockServer server;
    private MockServer upstream;
    private File served;

    @Before
    public void createFiles() throws IOException {
        served = temporaryFolder.newFolder("__files");
        write(new File(temporaryFolder.getRoot(), "secret.txt"), "outside-secret");
        write(new File(served, "hello.json"), "{\"hello\":\"world\"}");
        write(new File(served, "assets/site.css"), "body{}");
        write(new File(served, ".env"), "hidden-secret");
        write(new File(served, "mockserver/retrieve"), "control-plane-shadow");
    }

    @After
    public void stopServers() {
        for (MockServer running : new MockServer[]{server, upstream}) {
            if (running != null) {
                running.stop();
            }
        }
    }

    @Test
    public void shouldServeFileForUnmatchedGet() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);

        String response = send("GET", "/assets/site.css");

        assertThat(response, startsWith("HTTP/1.1 200 OK"));
        assertThat(response.toLowerCase(), containsString("content-type: text/css"));
        assertThat(response, endsWith("\r\n\r\nbody{}"));
        new MockServerClient("localhost", server.getLocalPort()).verify(request("/assets/site.css"), VerificationTimes.once());
    }

    @Test
    public void shouldAnswerHeadWithLengthAndNoBody() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);

        String response = send("HEAD", "/hello.json");

        assertThat(response, startsWith("HTTP/1.1 200 OK"));
        assertThat(response.toLowerCase(), containsString("content-type: application/json"));
        assertThat(response.toLowerCase(), containsString("content-length: 17"));
        assertThat(response, endsWith("\r\n\r\n"));
    }

    @Test
    public void shouldLetAMatchingExpectationWin() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);
        new MockServerClient("localhost", server.getLocalPort()).when(request("/hello.json")).respond(response("from expectation"));

        String response = send("GET", "/hello.json");

        assertThat(response, startsWith("HTTP/1.1 200 OK"));
        assertThat(response, endsWith("from expectation"));
    }

    @Test
    public void shouldReturnNotFoundForMissingFileAndOtherMethods() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);

        assertThat(send("GET", "/missing.json"), startsWith("HTTP/1.1 404 Not Found"));
        assertThat(send("GET", "/assets"), startsWith("HTTP/1.1 404 Not Found"));
        assertThat(send("POST", "/hello.json"), startsWith("HTTP/1.1 404 Not Found"));
    }

    @Test
    public void shouldNotServeFilesOutsideTheDirectoryOrHiddenFiles() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);

        for (String path : new String[]{
            "/../secret.txt",
            "/assets/../../secret.txt",
            "/%2e%2e/secret.txt",
            "/%2e%2e%2fsecret.txt",
            "/%252e%252e%252fsecret.txt",
            "/..%5csecret.txt",
            "/" + new File(temporaryFolder.getRoot(), "secret.txt").getAbsolutePath(),
            "/hello.json%00",
            "/.env",
            "/%2eenv",
        }) {
            String response = send("GET", path);
            assertThat(path, response, startsWith("HTTP/1.1 404 Not Found"));
            assertThat(path, response, not(containsString("outside-secret")));
            assertThat(path, response, not(containsString("hidden-secret")));
        }
    }

    @Test
    public void shouldNotServeControlPlanePaths() throws IOException {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);

        assertThat(send("GET", "/mockserver/retrieve"), not(containsString("control-plane-shadow")));
        assertThat(send("PUT", "/mockserver/status"), startsWith("HTTP/1.1 200 OK"));
    }

    @Test
    public void shouldForwardProxiedRequestsInsteadOfServingFiles() throws IOException {
        upstream = new MockServer(configuration(), 0);
        new MockServerClient("localhost", upstream.getLocalPort()).when(request("/hello.json")).respond(response("from upstream"));
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()).proxyRemoteHost("localhost").proxyRemotePort(upstream.getLocalPort()), 0);

        String response = send("GET", "/hello.json");

        assertThat(response, endsWith("from upstream"));
    }

    @Test
    public void shouldReturnNotFoundWhenNotConfigured() throws IOException {
        server = new MockServer(configuration(), 0);

        assertThat(send("GET", "/hello.json"), startsWith("HTTP/1.1 404 Not Found"));
    }

    @Test
    public void shouldRecordServedFileForVerificationAndRetrieveBelowInfo() throws IOException {
        server = new MockServer(configuration().logLevel("WARN").staticFilesDirectory(served.getPath()), 0);

        assertThat(send("GET", "/hello.json"), startsWith("HTTP/1.1 200 OK"));

        LogEventRequestAndResponse[] recorded = new MockServerClient("localhost", server.getLocalPort()).retrieveRecordedRequestsAndResponses(request("/hello.json"));
        assertThat(recorded.length, is(1));
        assertThat(recorded[0].getHttpResponse().getStatusCode(), is(200));
        String verification = "{\"httpRequest\":{\"path\":\"/hello.json\"},\"httpResponse\":{\"statusCode\":200},\"disposition\":\"MOCKED\",\"times\":{\"atLeast\":1,\"atMost\":1}}";
        assertThat(send("PUT", "/mockserver/verify", verification), startsWith("HTTP/1.1 202"));
    }

    @Test
    public void shouldStopOnlyTheStoppedServersStaticFileReadPool() throws Exception {
        server = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);
        upstream = new MockServer(configuration().staticFilesDirectory(served.getPath()), 0);
        MockServer other = upstream;
        assertThat(send("GET", "/hello.json"), startsWith("HTTP/1.1 200 OK"));
        assertThat(send(other, "GET", "/hello.json"), startsWith("HTTP/1.1 200 OK"));
        ExecutorService stoppedPool = server.getScheduler().getStaticFileReadExecutor();
        assertThat(stoppedPool, notNullValue());

        server.stop();

        assertThat(stoppedPool.awaitTermination(10, TimeUnit.SECONDS), is(true));
        assertThat(other.getScheduler().getStaticFileReadExecutor().isShutdown(), is(false));
        assertThat(send(other, "GET", "/hello.json"), startsWith("HTTP/1.1 200 OK"));
    }

    private String send(String method, String path) throws IOException {
        return send(server, method, path, "");
    }

    private String send(String method, String path, String body) throws IOException {
        return send(server, method, path, body);
    }

    private String send(MockServer target, String method, String path) throws IOException {
        return send(target, method, path, "");
    }

    private static String send(MockServer target, String method, String path, String body) throws IOException {
        try (Socket socket = new Socket("localhost", target.getLocalPort())) {
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            output.write((method + " " + path + " HTTP/1.1\r\nHost: localhost:" + target.getLocalPort() + "\r\nContent-Length: " + bodyBytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bodyBytes);
            output.flush();
            InputStream input = socket.getInputStream();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            input.transferTo(received);
            return received.toString(StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
