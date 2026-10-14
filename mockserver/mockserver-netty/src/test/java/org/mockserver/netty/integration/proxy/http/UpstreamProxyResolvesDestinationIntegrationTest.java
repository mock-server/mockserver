package org.mockserver.netty.integration.proxy.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.scheduler.Scheduler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * A proxy whose forwardHttpsProxy or forwardSocksProxy is set leaves the destination's name to that upstream proxy,
 * as networks where only the upstream proxy can resolve external names need. The upstream here is a test proxy that
 * records the destination it is asked for and connects every name to this machine, so a destination named by a
 * name this JVM cannot resolve is reachable only if the name reaches the upstream unresolved.
 *
 * @author jamesdbloom
 */
public class UpstreamProxyResolvesDestinationIntegrationTest {

    // RFC 6761: names under .invalid never resolve
    private static final String UNRESOLVABLE = "destination-only-the-upstream-proxy-resolves.invalid";
    private static final long TIMEOUT_SECONDS = 30;

    private static EventLoopGroup clientEventLoopGroup;
    private static ClientAndServer destinationClientAndServer;

    private RecordingUpstreamProxy upstreamProxy;
    private ClientAndServer proxyClientAndServer;

    @BeforeClass
    public static void startDestinationAndEventLoopGroup() {
        destinationClientAndServer = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(UpstreamProxyResolvesDestinationIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopDestinationAndEventLoopGroup() {
        stopQuietly(destinationClientAndServer);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void resetDestination() {
        assertThrows("this test needs a name this JVM cannot resolve", UnknownHostException.class, () -> InetAddress.getByName(UNRESOLVABLE));
        destinationClientAndServer.reset();
        destinationClientAndServer
            .when(request().withPath("/target"))
            .respond(response().withStatusCode(200).withBody("destination"));
    }

    @After
    public void stopProxies() throws IOException {
        stopQuietly(proxyClientAndServer);
        if (upstreamProxy != null) {
            upstreamProxy.close();
        }
    }

    @Test
    public void shouldLeaveSecureDestinationNameToForwardHttpsProxy() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.CONNECT, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardHttpsProxy(upstreamProxy.address()));

        HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/target").withSecure(true).withHeader(HOST.toString(), UNRESOLVABLE + ":" + destinationPort()));

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains(UNRESOLVABLE + ":" + destinationPort()));
    }

    @Test
    public void shouldLeaveDestinationNameOfConnectTunnelToForwardHttpsProxy() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.CONNECT, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardHttpsProxy(upstreamProxy.address()));

        // the client tunnels to the proxy with CONNECT, as a browser or HTTP client configured with an HTTPS proxy does
        HttpResponse response = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, Collections.singletonList(proxyConfiguration(ProxyConfiguration.Type.HTTPS, new InetSocketAddress("127.0.0.1", proxyClientAndServer.getLocalPort()))), false)
            .sendRequest(request().withPath("/target").withSecure(true).withHeader(HOST.toString(), UNRESOLVABLE + ":" + destinationPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains(UNRESOLVABLE + ":" + destinationPort()));
    }

    @Test
    public void shouldLeavePlainDestinationNameToForwardSocksProxy() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardSocksProxy(upstreamProxy.address()));

        HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/target").withHeader(HOST.toString(), UNRESOLVABLE + ":" + destinationPort()));

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains("domain " + UNRESOLVABLE + ":" + destinationPort()));
    }

    @Test
    public void shouldLeaveSecureDestinationNameToForwardSocksProxy() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardSocksProxy(upstreamProxy.address()));

        HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/target").withSecure(true).withHeader(HOST.toString(), UNRESOLVABLE + ":" + destinationPort()));

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains("domain " + UNRESOLVABLE + ":" + destinationPort()));
    }

    @Test
    public void shouldNotLookUpDestinationNameThatResolvesLocally() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardSocksProxy(upstreamProxy.address()));

        HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/target").withHeader(HOST.toString(), "localhost:" + destinationPort()));

        // a name looked up here would reach the SOCKS proxy as an address
        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains("domain localhost:" + destinationPort()));
    }

    @Test
    public void shouldSendNameOfDestinationResolvedByCallerToForwardSocksProxy() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS);
        NettyHttpClient client = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, Collections.singletonList(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, upstreamProxy.address())), true);

        HttpResponse response = client
            .sendRequest(request().withPath("/target").withHeader(HOST.toString(), "localhost:" + destinationPort()), new InetSocketAddress("localhost", destinationPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains("domain localhost:" + destinationPort()));
    }

    @Test
    public void shouldSendIpLiteralDestinationToForwardSocksProxyAsAnAddress() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardSocksProxy(upstreamProxy.address()));

        HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort()));

        assertReachedDestination(response);
        assertThat(upstreamProxy.destinations(), contains("ipv4 127.0.0.1:" + destinationPort()));
    }

    @Test
    public void shouldStillBlockForwardToPrivateNetworkWhenUpstreamProxyResolves() throws Exception {
        upstreamProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.CONNECT, TIMEOUT_SECONDS);
        proxyClientAndServer = startClientAndServer(configuration().forwardHttpsProxy(upstreamProxy.address()).forwardProxyBlockPrivateNetworks(true));
        // a name that resolves here to loopback, and one that does not resolve here: neither is vetted, so neither is sent
        for (String host : new String[]{"localhost", UNRESOLVABLE}) {
            proxyClientAndServer.reset();
            proxyClientAndServer
                .when(request().withPath("/forward"))
                .forward(forward().withHost(host).withPort(destinationPort()).withScheme(HttpForward.Scheme.HTTPS));

            HttpResponse response = sendTo(proxyClientAndServer, request().withPath("/forward").withHeader(HOST.toString(), "127.0.0.1:" + proxyClientAndServer.getLocalPort()));

            assertThat(host, response.getStatusCode(), is(502));
        }
        assertThat(upstreamProxy.destinations(), is(empty()));
        destinationClientAndServer.verify(request().withPath("/target"), exactly(0));
    }

    private static int destinationPort() {
        return destinationClientAndServer.getLocalPort();
    }

    private void assertReachedDestination(HttpResponse response) {
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("destination"));
        destinationClientAndServer.verify(request().withPath("/target"), exactly(1));
    }

    private static HttpResponse sendTo(ClientAndServer server, HttpRequest request) throws Exception {
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request, new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
