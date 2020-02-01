package org.mockserver.cli;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.PortFactory;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class MainTest {

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(MainTest.class.getSimpleName() + "-eventLoop"));

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        Main.usageShown = false;
    }

    @After
    public void clearUsageShown() {
        Main.usageShown = false;
    }

    @Test
    public void shouldStartMockServer() {
        // given
        final int freePort = PortFactory.findFreePort();
        MockServerClient mockServerClient = new MockServerClient("127.0.0.1", freePort);
        Level originalLogLevel = ConfigurationProperties.logLevel();

        try {
            // when
            Main.main(
                "-serverPort", String.valueOf(freePort),
                "-logLevel", "DEBUG"
            );

            // then
            assertThat(mockServerClient.hasStarted(), is(true));
            assertThat(ConfigurationProperties.logLevel().toString(), is("DEBUG"));
        } finally {
            ConfigurationProperties.logLevel(originalLogLevel.toString());
            stopQuietly(mockServerClient);
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
            Main.main(
                "-serverPort", String.valueOf(freePort),
                "-proxyRemotePort", String.valueOf(echoServer.getPort()),
                "-proxyRemoteHost", "127.0.0.1"
            );
            final HttpResponse response = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false)
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
            stopQuietly(mockServerClient);
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
            Main.main(
                "-serverPort", String.valueOf(freePort),
                "-proxyRemotePort", String.valueOf(echoServer.getPort())
            );
            final HttpResponse response = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false)
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
            stopQuietly(mockServerClient);
        }
    }

    @Test
    public void shouldPrintOutUsageForInvalidServerPort() throws UnsupportedEncodingException {
        // given
        PrintStream originalPrintStream = Main.systemOut;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Main.systemOut = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());

            // when
            Main.main("-serverPort", "A");

            // then
            String actual = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            String expected = NEW_LINE +
                "   =====================================================================================================" + NEW_LINE +
                "   serverPort value \"A\" is invalid, please specify a comma separated list of ports i.e. \"1080,1081,1082\"" + NEW_LINE +
                "   =====================================================================================================" + NEW_LINE +
                NEW_LINE +
                Main.USAGE;
            assertThat(actual, is(expected));
        } finally {
            Main.systemOut = originalPrintStream;
        }
    }

    @Test
    public void shouldPrintOutUsageForInvalidRemotePort() throws UnsupportedEncodingException {
        // given
        final int freePort = PortFactory.findFreePort();
        PrintStream originalPrintStream = Main.systemOut;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Main.systemOut = new PrintStream(baos, true, StandardCharsets.UTF_8.name());

            // when
            Main.main("-serverPort", String.valueOf(freePort), "-proxyRemotePort", "A");

            // then
            String actual = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            String expected = NEW_LINE +
                "   =======================================================================" + NEW_LINE +
                "   proxyRemotePort value \"A\" is invalid, please specify a port i.e. \"1080\"" + NEW_LINE +
                "   =======================================================================" + NEW_LINE +
                NEW_LINE +
                Main.USAGE;
            assertThat(actual, is(expected));
        } finally {
            Main.systemOut = originalPrintStream;
        }
    }

    @Test
    public void shouldPrintOutUsageForInvalidRemoteHost() throws UnsupportedEncodingException {
        // given
        final int freePort = PortFactory.findFreePort();
        PrintStream originalPrintStream = Main.systemOut;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Main.systemOut = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());

            // when
            Main.main("-serverPort", String.valueOf(freePort), "-proxyRemoteHost", "%^&*(");

            // then
            String actual = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            String expected = NEW_LINE +
                "   ====================================================================================================" + NEW_LINE +
                "   proxyRemoteHost value \"%^&*(\" is invalid, please specify a host name i.e. \"localhost\" or \"127.0.0.1\"" + NEW_LINE +
                "   ====================================================================================================" + NEW_LINE +
                NEW_LINE +
                Main.USAGE;
            assertThat(actual, is(expected));
        } finally {
            Main.systemOut = originalPrintStream;
        }
    }

    @Test
    public void shouldPrintOutUsageForInvalidLogLevel() throws UnsupportedEncodingException {
        // given
        final int freePort = PortFactory.findFreePort();
        PrintStream originalPrintStream = Main.systemOut;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            Main.systemOut = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());

            // when
            Main.main("-serverPort", String.valueOf(freePort), "-logLevel", "FOO");

            // then
            String actual = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            String expected = NEW_LINE +
                "   ====================================================================================================================================================================================================" + NEW_LINE +
                "   logLevel value \"FOO\" is invalid, please specify one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\" or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\"" + NEW_LINE +
                "   ====================================================================================================================================================================================================" + NEW_LINE +
                NEW_LINE +
                Main.USAGE;
            assertThat(actual, is(expected));
        } finally {
            Main.systemOut = originalPrintStream;
        }
    }

}
