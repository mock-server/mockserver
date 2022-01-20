package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.integration.mock.initializer.ExpectationInitializerIntegrationExample;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerIntegrationTest {

    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void createClientAndEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ExpectationInitializerIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldLoadExpectationsFromJson() throws Exception {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        MockServer mockServer = null;

        try {
            // when
            ConfigurationProperties.initializationJsonPath("org/mockserver/netty/integration/mock/initializer/initializerJson.json");
            mockServer = new MockServer();

            // then
            assertThat(
                httpClient.sendRequest(
                    request()
                        .withMethod("GET")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/simpleFirst")
                ).get(10, TimeUnit.SECONDS).getBodyAsString(),
                is("some first response")
            );
            assertThat(
                httpClient.sendRequest(
                    request()
                        .withMethod("GET")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/simpleSecond")
                ).get(10, TimeUnit.SECONDS).getBodyAsString(),
                is("some second response")
            );
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            stopQuietly(mockServer);
        }
    }

    @Test
    public void shouldLoadExpectationsFromInitializerClass() throws Exception {
        // given
        String initializationClass = ConfigurationProperties.initializationClass();
        MockServer mockServer = null;

        try {
            // when
            ConfigurationProperties.initializationClass(ExpectationInitializerIntegrationExample.class.getName());
            mockServer = new MockServer();

            // then
            assertThat(
                httpClient.sendRequest(
                    request()
                        .withMethod("GET")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/simpleFirst")
                ).get(10, TimeUnit.SECONDS).getBodyAsString(),
                is("some first response")
            );
            assertThat(
                httpClient.sendRequest(
                    request()
                        .withMethod("GET")
                        .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                        .withPath("/simpleSecond")
                ).get(10, TimeUnit.SECONDS).getBodyAsString(),
                is("some second response")
            );
        } finally {
            ConfigurationProperties.initializationClass(initializationClass);
            stopQuietly(mockServer);
        }
    }

}
