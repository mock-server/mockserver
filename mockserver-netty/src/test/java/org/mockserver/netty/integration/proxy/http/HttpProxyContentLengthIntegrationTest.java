package org.mockserver.netty.integration.proxy.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.scheduler.Scheduler;

import static java.util.concurrent.TimeUnit.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class HttpProxyContentLengthIntegrationTest {

    private static ClientAndServer targetClientAndServer;
    private static ClientAndServer proxyClientAndServer;

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(HttpProxyContentLengthIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @BeforeClass
    public static void startServer() {
        targetClientAndServer = startClientAndServer();
        proxyClientAndServer = startClientAndServer();
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(targetClientAndServer);
        stopQuietly(proxyClientAndServer);

    }

    @Before
    public void reset() {
        targetClientAndServer.reset();
        proxyClientAndServer.reset();
    }

    @Test
    public void shouldHandleProxiedResponseWithoutContentLength() throws Exception {
        // given
        targetClientAndServer
            .when(
                request()
                    .withPath("/noContentLengthHeader")
            )
            .respond(
                response()
                    .withBody(
                        xml("" +
                            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + NEW_LINE +
                            "<MockedDocument>" + NEW_LINE +
                            "  <SomeResponse>" + NEW_LINE +
                            "    <ElementOne>Hello</ElementOne>" + NEW_LINE +
                            "  </SomeResponse>" + NEW_LINE +
                            "</MockedDocument>")
                    )
                    .withConnectionOptions(
                        connectionOptions()
                            .withSuppressContentLengthHeader(true)
                            .withCloseSocket(true)
                    )
            );

        // when
        HttpResponse httpResponse = new NettyHttpClient(
            new MockServerLogger(),
            clientEventLoopGroup,
            proxyConfiguration(
                ProxyConfiguration.Type.HTTPS,
                "localhost:" + proxyClientAndServer.getLocalPort()
            ), false)
            .sendRequest(
                request()
                    .withPath("/noContentLengthHeader")
                    .withHeader("Host", "localhost:" + targetClientAndServer.getLocalPort())
            )
            .get(10, SECONDS);

        // then
        assertThat(httpResponse.getBodyAsString(), is("" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + NEW_LINE +
            "<MockedDocument>" + NEW_LINE +
            "  <SomeResponse>" + NEW_LINE +
            "    <ElementOne>Hello</ElementOne>" + NEW_LINE +
            "  </SomeResponse>" + NEW_LINE +
            "</MockedDocument>"));
    }

}
