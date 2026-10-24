package org.mockserver.netty.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3Headers;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.netty.MockServer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A static file is served over HTTP/3 as over HTTP/1.1 and HTTP/2, with the read
 * handed to the static file pool. Skipped when the QUIC native library is missing.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3StaticFilesIntegrationTest {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static MockServer mockServer;
    private static NioEventLoopGroup clientGroup;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable notLoaded) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 static files test", available);
        File served = temporaryFolder.newFolder("__files");
        Files.write(new File(served, "hello.json").toPath(), "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
        mockServer = startWithHttp3(configuration()
            .http3MaxIdleTimeout(30000L)
            .attemptToProxyIfNoMatchingExpectation(false)
            .staticFilesDirectory(served.getPath()));
        clientGroup = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shouldServeStaticFileOverHttp3() throws Exception {
        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange get = client.send(requestHeaders(HttpMethod.GET, "/hello.json"));
            assertThat(get.status(), is(200));
            assertThat(get.headers().get("content-type").toString(), is("application/json"));
            assertThat(get.body(), is("{\"hello\":\"world\"}"));

            Http3TestClient.Exchange head = client.send(requestHeaders(HttpMethod.HEAD, "/hello.json"));
            assertThat(head.status(), is(200));
            assertThat(head.body(), is(""));
            assertThat(head.headers().get("content-length").toString(), is("17"));

            assertThat(client.send(requestHeaders(HttpMethod.GET, "/missing.json")).status(), is(404));
        }
    }

    private static Http3Headers requestHeaders(HttpMethod method, String path) {
        return new DefaultHttp3Headers()
            .method(method.asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }
}
