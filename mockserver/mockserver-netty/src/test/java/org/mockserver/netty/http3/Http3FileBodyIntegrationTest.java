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
import org.mockserver.client.MockServerClient;
import org.mockserver.model.FileBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;
import org.mockserver.netty.MockServer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * HTTP/3 processes requests synchronously on its stream's event loop; a matched FILE body is still served, with the
 * read handed to the file read pool. Skipped when the QUIC native library is missing.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3FileBodyIntegrationTest {

    private static final String TEXT = "{\"greeting\":\"héllo ✓\"}";
    private static final MediaType TEXT_TYPE = MediaType.parse("application/json; charset=utf-8");

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static NioEventLoopGroup clientGroup;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable notLoaded) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 FILE body test", available);
        File mocks = temporaryFolder.newFolder("mocks");
        File text = new File(mocks, "text.json");
        Files.write(text.toPath(), TEXT.getBytes(StandardCharsets.UTF_8));
        mockServer = startWithHttp3(configuration().http3MaxIdleTimeout(30000L));
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request("/file")).respond(response().withBody(new FileBody(text.getAbsolutePath(), TEXT_TYPE)));
        mockServerClient.when(request("/string")).respond(response().withBody(new StringBody(TEXT, TEXT_TYPE)));
        mockServerClient.when(request("/missing")).respond(response().withBody(new FileBody(new File(mocks, "missing.json").getAbsolutePath(), MediaType.APPLICATION_JSON)));
        clientGroup = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shouldServeFileBodyOverHttp3FromFileReadPool() throws Exception {
        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange file = client.send(requestHeaders("/file"));
            Http3TestClient.Exchange inline = client.send(requestHeaders("/string"));
            assertThat(file.status(), is(200));
            assertThat(file.body(), is(TEXT));
            assertThat(file.headers().toString(), is(inline.headers().toString()));

            Http3TestClient.Exchange missing = client.send(requestHeaders("/missing"));
            assertThat(missing.status(), is(500));
            assertThat(missing.body(), is("Error reading file for response body"));
        }
        ThreadPoolExecutor fileReads = (ThreadPoolExecutor) mockServer.getScheduler().getFileReadExecutor();
        assertThat(fileReads, notNullValue());
        assertThat(fileReads.getCompletedTaskCount(), greaterThanOrEqualTo(2L));
    }

    private static Http3Headers requestHeaders(String path) {
        return new DefaultHttp3Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }
}
