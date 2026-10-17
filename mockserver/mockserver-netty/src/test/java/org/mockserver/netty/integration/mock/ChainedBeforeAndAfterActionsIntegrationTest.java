package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
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
import static org.mockserver.model.AfterAction.afterAction;
import static org.mockserver.model.AfterAction.beforeAction;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * Chaining the singular {@code withBeforeAction} / {@code withAfterAction} on the Java client registers every
 * action, so each webhook fires when the expectation matches.
 */
public class ChainedBeforeAndAfterActionsIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    private static ClientAndServer server;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServer() {
        server = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(ChainedBeforeAndAfterActionsIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(server);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldRunEveryChainedSingularBeforeAndAfterAction() throws Exception {
        // given
        for (String hook : new String[]{"/before-one", "/before-two", "/after-one", "/after-two"}) {
            server.when(request().withPath(hook)).respond(response().withStatusCode(200));
        }
        server
            .when(request().withPath("/trigger"))
            .withBeforeAction(beforeAction().withHttpRequest(hook("/before-one")))
            .withBeforeAction(beforeAction().withHttpRequest(hook("/before-two")))
            .withAfterAction(afterAction().withHttpRequest(hook("/after-one")))
            .withAfterAction(afterAction().withHttpRequest(hook("/after-two")))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request().withPath("/trigger").withHeader(HOST.toString(), "127.0.0.1:" + server.getLocalPort()), new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(response.getBodyAsString(), is("triggered"));
        tryWaitForSuccess(() -> server.verify(request().withPath("/before-one"), exactly(1)));
        tryWaitForSuccess(() -> server.verify(request().withPath("/before-two"), exactly(1)));
        tryWaitForSuccess(() -> server.verify(request().withPath("/after-one"), exactly(1)));
        tryWaitForSuccess(() -> server.verify(request().withPath("/after-two"), exactly(1)));
    }

    private static HttpRequest hook(String path) {
        return request().withPath(path).withHeader(HOST.toString(), "127.0.0.1:" + server.getLocalPort());
    }
}
