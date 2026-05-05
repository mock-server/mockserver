package org.mockserver.maven;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("deprecation")
public class InstanceHolderTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static EventLoopGroup clientEventLoopGroup;
    private static NettyHttpClient httpClient;

    @BeforeClass
    public static void createClientAndEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup();
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldStartMockServer() {
        // given
        final int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1", freePort);
        Level originalLogLevel = ConfigurationProperties.logLevel();

        try {
            // when
            new InstanceHolder().start(
                    new Integer[]{freePort},
                    -1,
                    null,
                    "DEBUG",
                    null,
                    "");

            // then
            assertThat(mockServerClient.hasStarted(), is(true));
            assertThat(ConfigurationProperties.logLevel().toString(), is("DEBUG"));
        } finally {
            ConfigurationProperties.logLevel(originalLogLevel.toString());
            mockServerClient.stop();
        }
    }

    @Test
    public void shouldStartMockServerWithRemotePortAndHost() {
        // given
        final int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1", freePort);
        try {
            EchoServer echoServer = new EchoServer(false);
            echoServer.withNextResponse(response("port_forwarded_response"));

            // when
            new InstanceHolder().start(
                    new Integer[]{freePort},
                    echoServer.getPort(),
                    "127.0.0.1",
                    "DEBUG",
                    null,
                    "");
            final HttpResponse response =
                    httpClient
                            .sendRequest(
                                    request()
                                            .withHeader(HOST.toString(), "127.0.0.1:" + freePort),
                                    10,
                                    TimeUnit.SECONDS
                            );

            // then
            assertThat(mockServerClient.hasStarted(), is(true));
            assertThat(response.getBodyAsString(), is("port_forwarded_response"));
        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void shouldStartMockServerWithRemotePort() {
        // given
        final int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1", freePort);
        try {
            EchoServer echoServer = new EchoServer(false);
            echoServer.withNextResponse(response("port_forwarded_response"));

            // when
            new InstanceHolder().start(
                    new Integer[]{freePort},
                    echoServer.getPort(),
                    "",
                    "DEBUG",
                    null,
                    "");
            final HttpResponse response =
                    httpClient
                            .sendRequest(
                                    request()
                                            .withHeader(HOST.toString(), "127.0.0.1:" + freePort),
                                    10,
                                    TimeUnit.SECONDS
                            );

            // then
            assertThat(mockServerClient.hasStarted(), is(true));
            assertThat(response.getBodyAsString(), is("port_forwarded_response"));
        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void shouldPrintOutUsageForInvalidLogLevel() {
        // given
        final int freePort = PortFactory.findFreePort();

        // when
        try {
            new InstanceHolder().start(
                    new Integer[]{freePort},
                    -1,
                    "",
                    "WRONG",
                    null,
                    "");
            fail();
        } catch (Exception iae) {
            // then
            assertThat(iae, instanceOf(IllegalArgumentException.class));
            assertThat(iae.getMessage(), is("log level \"WRONG\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\", or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\""));
        }
    }
}
