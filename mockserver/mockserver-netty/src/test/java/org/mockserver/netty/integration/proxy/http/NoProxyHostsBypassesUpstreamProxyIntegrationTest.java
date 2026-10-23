package org.mockserver.netty.integration.proxy.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * A destination on noProxyHosts is connected to directly, whichever upstream proxy (forwardHttpProxy,
 * forwardHttpsProxy or forwardSocksProxy) would otherwise carry it, as no_proxy works for curl and the JVM. An entry is
 * a host name, a domain suffix ({@code *.example.com} or {@code .example.com}) or an IP address, which matches only a
 * destination given as that address. Destinations named here by a name under {@code .internal.test} carry the loopback
 * address, so a direct connection needs no lookup while the proxy, sent the name, connects it to this machine.
 *
 * @author jamesdbloom
 */
public class NoProxyHostsBypassesUpstreamProxyIntegrationTest {

    private static final String NAME = "api.internal.test";
    private static final long TIMEOUT_SECONDS = 30;

    private static EventLoopGroup clientEventLoopGroup;
    private static ClientAndServer destinationClientAndServer;

    private RecordingUpstreamProxy upstreamProxy;
    private ClientAndServer proxyClientAndServer;

    @BeforeClass
    public static void startDestinationAndEventLoopGroup() {
        destinationClientAndServer = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(NoProxyHostsBypassesUpstreamProxyIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopDestinationAndEventLoopGroup() {
        stopQuietly(destinationClientAndServer);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void resetDestination() {
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

    // forwardHttpProxy

    @Test
    public void shouldBypassForwardHttpProxyForHostNameEntryMatchingTheHostHeader() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, "localhost");

        // no address, as an override-forwarded request is sent
        assertConnectedDirectly(client.sendRequest(target(false, "localhost"), null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldBypassForwardHttpProxyForIpAddressEntryMatchingTheHostHeader() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, "127.0.0.1");

        assertConnectedDirectly(client.sendRequest(target(false, "127.0.0.1"), null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldBypassForwardHttpProxyForWildcardDomainSuffixEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, "*.internal.test");

        assertConnectedDirectly(send(client, false, named(NAME)));
    }

    @Test
    public void shouldBypassForwardHttpProxyForLeadingDotDomainSuffixEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, ".internal.test");

        assertConnectedDirectly(send(client, false, named(NAME)));
    }

    @Test
    public void shouldNotMatchIpAddressEntryToDestinationNamedByHostName() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, "127.0.0.1");

        // resolved by the caller, but named: an IP-address entry is not matched by a name's address
        HttpResponse response = send(client, false, new InetSocketAddress("localhost", destinationPort()));

        assertSentThroughHttpProxy(response, "http://localhost:" + destinationPort() + "/target");
    }

    @Test
    public void shouldSendDestinationNotOnTheListThroughForwardHttpProxy() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTP, "other.test,*.other.test,127.0.0.2");

        HttpResponse response = client.sendRequest(target(false, "localhost")).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertSentThroughHttpProxy(response, "http://localhost:" + destinationPort() + "/target");
    }

    // forwardHttpsProxy

    @Test
    public void shouldBypassForwardHttpsProxyForHostNameEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTPS, NAME);

        assertConnectedDirectly(send(client, true, named(NAME)));
    }

    @Test
    public void shouldBypassForwardHttpsProxyForDomainSuffixEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTPS, "*.internal.test");

        assertConnectedDirectly(send(client, true, named(NAME)));
    }

    @Test
    public void shouldBypassForwardHttpsProxyForIpAddressEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTPS, "127.0.0.1");

        assertConnectedDirectly(send(client, true, new InetSocketAddress("127.0.0.1", destinationPort())));
    }

    @Test
    public void shouldBypassForwardHttpsProxyForHostNameEntryMatchingTheHostHeader() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTPS, "localhost");

        assertConnectedDirectly(client.sendRequest(target(true, "localhost")).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldSendDestinationNotOnTheListThroughForwardHttpsProxy() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.HTTPS, "other.test,*.other.test,127.0.0.2");

        assertTunnelled(send(client, true, named(NAME)), NAME + ":" + destinationPort());
    }

    // forwardSocksProxy

    @Test
    public void shouldBypassForwardSocksProxyForHostNameEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.SOCKS5, NAME);

        assertConnectedDirectly(send(client, false, named(NAME)));
    }

    @Test
    public void shouldBypassForwardSocksProxyForDomainSuffixEntryOnSecureRequest() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.SOCKS5, "*.internal.test");

        assertConnectedDirectly(send(client, true, named(NAME)));
    }

    @Test
    public void shouldBypassForwardSocksProxyForIpAddressEntry() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.SOCKS5, "127.0.0.1");

        assertConnectedDirectly(send(client, false, new InetSocketAddress("127.0.0.1", destinationPort())));
    }

    @Test
    public void shouldLookUpUnresolvedDestinationOnTheListWhenConnectingDirectly() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.SOCKS5, "localhost");

        // as a forward action passes its target: the name is looked up here, not left to the proxy
        assertConnectedDirectly(send(client, true, InetSocketAddress.createUnresolved("localhost", destinationPort())));
    }

    @Test
    public void shouldSendDestinationNotOnTheListThroughForwardSocksProxy() throws Exception {
        NettyHttpClient client = clientThrough(ProxyConfiguration.Type.SOCKS5, "other.test,*.other.test,127.0.0.2");

        assertTunnelled(send(client, false, named(NAME)), "domain " + NAME + ":" + destinationPort());
    }

    // through MockServer: a forward with no socket address goes to its Host header

    @Test
    public void shouldForwardDirectlyToHostOnTheListInsteadOfThroughForwardHttpAndHttpsProxy() throws Exception {
        try (RecordingUpstreamProxy httpProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.HTTP, TIMEOUT_SECONDS);
             RecordingUpstreamProxy httpsProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.CONNECT, TIMEOUT_SECONDS)) {
            assertForwardedDirectly(configuration().forwardHttpProxy(httpProxy.address()).forwardHttpsProxy(httpsProxy.address()), httpProxy, httpsProxy);
        }
    }

    @Test
    public void shouldForwardDirectlyToHostOnTheListInsteadOfThroughForwardSocksProxy() throws Exception {
        try (RecordingUpstreamProxy socksProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.SOCKS5, TIMEOUT_SECONDS)) {
            assertForwardedDirectly(configuration().forwardSocksProxy(socksProxy.address()), socksProxy);
        }
    }

    @Test
    public void shouldStillBlockPrivateNetworkForHostOnTheList() throws Exception {
        try (RecordingUpstreamProxy httpProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.HTTP, TIMEOUT_SECONDS);
             RecordingUpstreamProxy httpsProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.CONNECT, TIMEOUT_SECONDS)) {
            proxyClientAndServer = startClientAndServer(configuration().forwardHttpProxy(httpProxy.address()).forwardHttpsProxy(httpsProxy.address()).noProxyHosts("localhost").forwardProxyBlockPrivateNetworks(true));
            overrideForwardToDestination();

            for (String path : new String[]{"/clear", "/secure"}) {
                assertThat(path, sendToProxy(path).getStatusCode(), is(502));
            }

            destinationClientAndServer.verify(request().withPath("/target"), exactly(0));
            assertThat(httpProxy.destinations(), is(empty()));
            assertThat(httpsProxy.destinations(), is(empty()));
        }
    }

    @Test
    public void shouldKeepTheStartupNoProxyHostsWhenAPutTriesToRouteAroundTheUpstreamProxy() throws Exception {
        try (RecordingUpstreamProxy httpProxy = new RecordingUpstreamProxy(RecordingUpstreamProxy.Protocol.HTTP, TIMEOUT_SECONDS)) {
            proxyClientAndServer = startClientAndServer(configuration().forwardHttpProxy(httpProxy.address()).noProxyHosts("other.test"));
            overrideForwardToDestination();

            proxyClientAndServer.updateConfiguration("{\"noProxyHosts\": \"localhost\"}");

            assertThat(ObjectMapperFactory.createObjectMapper().readTree(proxyClientAndServer.retrieveConfiguration()).path("noProxyHosts").asText(), is("other.test"));
            HttpResponse response = sendToProxy("/clear");
            assertThat(response.getStatusCode(), is(200));
            assertThat(response.getBodyAsString(), is(RecordingUpstreamProxy.HTTP_PROXY_BODY));
            destinationClientAndServer.verify(request().withPath("/target"), exactly(0));
            assertThat(httpProxy.destinations(), contains("http://localhost:" + destinationPort() + "/target"));
        }
    }

    private NettyHttpClient clientThrough(ProxyConfiguration.Type type, String noProxyHosts) throws IOException {
        RecordingUpstreamProxy.Protocol protocol = type == ProxyConfiguration.Type.HTTP ? RecordingUpstreamProxy.Protocol.HTTP
            : type == ProxyConfiguration.Type.HTTPS ? RecordingUpstreamProxy.Protocol.CONNECT
            : RecordingUpstreamProxy.Protocol.SOCKS5;
        upstreamProxy = new RecordingUpstreamProxy(protocol, TIMEOUT_SECONDS);
        return new NettyHttpClient(configuration().noProxyHosts(noProxyHosts), new MockServerLogger(), clientEventLoopGroup, Collections.singletonList(proxyConfiguration(type, upstreamProxy.address())), true);
    }

    private void assertForwardedDirectly(Configuration upstreamProxies, RecordingUpstreamProxy... recorders) throws Exception {
        proxyClientAndServer = startClientAndServer(upstreamProxies.noProxyHosts("localhost"));
        overrideForwardToDestination();

        for (String path : new String[]{"/clear", "/secure"}) {
            HttpResponse response = sendToProxy(path);
            assertThat(path, response.getStatusCode(), is(200));
            assertThat(path, response.getBodyAsString(), is("destination"));
        }

        destinationClientAndServer.verify(request().withPath("/target"), exactly(2));
        for (RecordingUpstreamProxy recorder : recorders) {
            assertThat(recorder.destinations(), is(empty()));
        }
    }

    private void overrideForwardToDestination() {
        proxyClientAndServer
            .when(request().withPath("/clear"))
            .forward(forwardOverriddenRequest(target(false, "localhost")));
        proxyClientAndServer
            .when(request().withPath("/secure"))
            .forward(forwardOverriddenRequest(target(true, "localhost")));
    }

    private HttpResponse sendToProxy(String path) throws Exception {
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request().withPath(path).withHeader(HOST.toString(), "127.0.0.1:" + proxyClientAndServer.getLocalPort()), new InetSocketAddress("127.0.0.1", proxyClientAndServer.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static HttpRequest target(boolean secure, String host) {
        return request().withPath("/target").withSecure(secure).withHeader(HOST.toString(), host + ":" + destinationPort());
    }

    private static HttpResponse send(NettyHttpClient client, boolean secure, InetSocketAddress destination) throws Exception {
        return client
            .sendRequest(target(secure, destination.getHostString()), destination)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static InetSocketAddress named(String name) throws IOException {
        return named(name, destinationPort());
    }

    /**
     * A destination named {@code name} that carries the loopback address, so connecting to it directly looks nothing up.
     */
    private static InetSocketAddress named(String name, int port) throws IOException {
        return new InetSocketAddress(InetAddress.getByAddress(name, new byte[]{127, 0, 0, 1}), port);
    }

    private static int destinationPort() {
        return destinationClientAndServer.getLocalPort();
    }

    private void assertConnectedDirectly(HttpResponse response) {
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("destination"));
        destinationClientAndServer.verify(request().withPath("/target"), exactly(1));
        assertThat(upstreamProxy.destinations(), is(empty()));
    }

    private void assertSentThroughHttpProxy(HttpResponse response, String requestTarget) {
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is(RecordingUpstreamProxy.HTTP_PROXY_BODY));
        destinationClientAndServer.verify(request().withPath("/target"), exactly(0));
        assertThat(upstreamProxy.destinations(), contains(requestTarget));
    }

    private void assertTunnelled(HttpResponse response, String destination) {
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("destination"));
        destinationClientAndServer.verify(request().withPath("/target"), exactly(1));
        assertThat(upstreamProxy.destinations(), contains(destination));
    }
}
