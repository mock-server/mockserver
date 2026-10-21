package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * {@code MockServerClient.importDocument} returns the upserted expectations for every import reply shape: the array a
 * HAR import replies with, and the {@code {"expectations":[...],"warnings":[...]}} object of a WireMock import.
 */
public class ClientImportDocumentIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    // a custom transformer cannot be mapped, so the server replies with a warning alongside the expectation
    private static final String WIREMOCK_MAPPINGS = """
        {
          "mappings": [
            {
              "request": { "method": "GET", "urlPath": "/wiremock/greeting" },
              "response": { "status": 200, "body": "hello from wiremock", "transformers": [ "my-transformer" ] }
            }
          ]
        }
        """;

    private static final String WIREMOCK_TEMPLATE = """
        {
          "request": { "method": "GET", "urlPath": "/wiremock/echo" },
          "response": {
            "status": 200,
            "headers": { "Content-Type": "text/plain" },
            "body": "{{request.method}} {{request.path}} for {{request.query.name}} via {{request.headers.X-Client}}",
            "transformers": [ "response-template" ]
          }
        }
        """;

    private static final String HAR = """
        {
          "log": {
            "version": "1.2",
            "entries": [
              {
                "request": { "method": "GET", "url": "http://api.example.com/har/greeting", "headers": [] },
                "response": { "status": 200, "headers": [], "content": { "text": "hello from har" } }
              }
            ]
          }
        }
        """;

    private static ClientAndServer server;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServer() {
        server = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(ClientImportDocumentIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(server);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void reset() {
        server.reset();
    }

    @Test
    public void shouldReturnTheExpectationsOfAWireMockImportThatRepliesWithWarnings() throws Exception {
        // when
        Expectation[] imported = server.importDocument(WIREMOCK_MAPPINGS, "wiremock");

        // then
        assertThat(imported.length, is(1));
        assertThat(((HttpRequest) imported[0].getHttpRequest()).getPath().getValue(), is("/wiremock/greeting"));
        assertThat(send(request().withMethod("GET").withPath("/wiremock/greeting")).getBodyAsString(), is("hello from wiremock"));
    }

    @Test
    public void shouldServeAnImportedWireMockResponseTemplate() throws Exception {
        // when
        server.importDocument(WIREMOCK_TEMPLATE, "wiremock");

        // then
        HttpResponse response = send(request().withMethod("GET").withPath("/wiremock/echo")
            .withQueryStringParameter("name", "Ada").withHeader("X-Client", "cli"));
        assertThat(response.getBodyAsString(), is("GET /wiremock/echo for Ada via cli"));
    }

    @Test
    public void shouldReturnTheExpectationsOfAnAutoDetectedWireMockImport() {
        // when
        Expectation[] imported = server.importDocument(WIREMOCK_MAPPINGS, null);

        // then
        assertThat(imported.length, is(1));
        assertThat(((HttpRequest) imported[0].getHttpRequest()).getPath().getValue(), is("/wiremock/greeting"));
    }

    @Test
    public void shouldReturnTheExpectationsOfAHarImport() throws Exception {
        // when
        Expectation[] imported = server.importHar(HAR);

        // then
        assertThat(imported.length, is(1));
        assertThat(((HttpRequest) imported[0].getHttpRequest()).getPath().getValue(), is("/har/greeting"));
        assertThat(send(request().withMethod("GET").withPath("/har/greeting")).getBodyAsString(), is("hello from har"));
    }

    private static HttpResponse send(HttpRequest request) throws Exception {
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request.withHeader(HOST.toString(), "127.0.0.1:" + server.getLocalPort()), new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
