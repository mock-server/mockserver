package org.mockserver.httpclient;

import io.netty.channel.ConnectTimeoutException;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.httpclient.UpstreamConnectFailure.OUT_OF_LOCAL_RESOURCES;
import static org.mockserver.httpclient.UpstreamConnectFailure.REFUSED;
import static org.mockserver.httpclient.UpstreamConnectFailure.TIMED_OUT;
import static org.mockserver.httpclient.UpstreamConnectFailure.TIMED_OUT_CONFIGURABLE;
import static org.mockserver.httpclient.UpstreamConnectFailure.UNREACHABLE;
import static org.mockserver.httpclient.UpstreamConnectFailure.UNRESOLVED;
import static org.mockserver.httpclient.UpstreamConnectFailure.reason;
import static org.mockserver.httpclient.UpstreamConnectFailure.targetsReceivingAddress;

public class UpstreamConnectFailureTest {

    @Test
    public void shouldNameAHostNameThatDoesNotResolve() {
        assertThat(reason(new ExecutionException(new UnknownHostException("upstream.invalid")), true), is(UNRESOLVED));
        assertThat(reason(new UnresolvedAddressException(), true), is(UNRESOLVED));
    }

    @Test
    public void shouldNameARefusedConnectionAsTheJdkAndNettysNativeTransportsReportIt() {
        assertThat(reason(new CompletionException(new ConnectException("Connection refused: upstream.example/127.0.0.1:8080")), true), is(REFUSED));
        assertThat(reason(new ConnectException("connect(..) failed: Connection refused"), true), is(REFUSED));
    }

    @Test
    public void shouldNameAConnectThatTimedOutAndTheTimeoutsPropertyOnlyWhereItApplies() {
        assertThat(reason(new ConnectTimeoutException("connection timed out after 250 ms: /192.0.2.1:9"), true), is(TIMED_OUT_CONFIGURABLE));
        assertThat(reason(new ConnectTimeoutException("connection timed out after 1000 ms: /192.0.2.1:9"), false), is(TIMED_OUT));
        assertThat(reason(new ConnectException("connect(..) failed: Connection timed out"), true), is(TIMED_OUT_CONFIGURABLE));
        assertThat("by its class, whatever its message says", reason(new ConnectTimeoutException(), true), is(TIMED_OUT_CONFIGURABLE));
    }

    @Test
    public void shouldNotTakeATimeoutOnAnEstablishedConnectionForAConnectTimeout() {
        assertThat(reason(new SocketException("Connection timed out"), true), is(nullValue()));
        assertThat(reason(new SocketTimeoutException("Read timed out"), true), is(nullValue()));
    }

    @Test
    public void shouldNameAnAddressWithNoRouteToIt() {
        assertThat(reason(new NoRouteToHostException("No route to host"), true), is(UNREACHABLE));
        assertThat(reason(new SocketException("Network is unreachable"), true), is(UNREACHABLE));
    }

    @Test
    public void shouldNameLocalPortsOrFilesRunningOut() {
        SocketException annotated = new SocketException("Can't assign requested address: /127.0.0.1:1080");
        annotated.initCause(new BindException("Can't assign requested address"));
        assertThat(reason(annotated, true), is(OUT_OF_LOCAL_RESOURCES));
        assertThat(reason(new BindException("Cannot assign requested address"), true), is(OUT_OF_LOCAL_RESOURCES));
        assertThat(reason(new SocketException("Too many open files"), true), is(OUT_OF_LOCAL_RESOURCES));
        assertThat("epoll: a channel that cannot be created", reason(new io.netty.channel.ChannelException("Unable to create Channel from class class io.netty.channel.epoll.EpollSocketChannel", new IOException("newSocketStream(..) failed: Too many open files")), true), is(OUT_OF_LOCAL_RESOURCES));
    }

    @Test
    public void shouldNameAnUpstreamProxyThatCouldNotBeReachedRatherThanTheUpstream() {
        InetSocketAddress proxy = InetSocketAddress.createUnresolved("proxy.example", 3128);

        assertThat(reason(new ExecutionException(new UpstreamProxyUnreachableException(proxy, "forwardHttpProxy", new ConnectException("Connection refused"))), true),
            is("upstream proxy set by forwardHttpProxy could not be reached (it refused the connection): check the proxy is running and its host and port are correct"));
        assertThat(reason(new UpstreamProxyUnreachableException(proxy, "forwardSocksProxy", new UnknownHostException("proxy.example")), true),
            is("upstream proxy set by forwardSocksProxy could not be reached (its host name could not be resolved): check the proxy is running and its host and port are correct"));
        assertThat(reason(new UpstreamProxyUnreachableException(proxy, "forwardHttpsProxy", new SocketException("something else")), true),
            is("upstream proxy set by forwardHttpsProxy could not be reached (the connection failed): check the proxy is running and its host and port are correct"));
    }

    @Test
    public void shouldNameAPooledConnectionTheUpstreamClosed() {
        Throwable closed = new PooledConnectionClosedException("PATCH", new SocketConnectionException("Channel handler removed before valid response has been received"));

        assertThat(reason(new ExecutionException(closed), true),
            is("upstream closed the pooled keep-alive connection the request was sent on without answering, and a PATCH is not sent again in case the upstream acted on it: if the upstream closes idle connections, set forwardConnectionPoolIdleTimeoutMillis below its keep-alive timeout or set forwardConnectionPoolEnabled to false; otherwise the upstream may have closed the connection to reject the request"));
        assertThat("reads as its cause", closed.toString(), is("org.mockserver.httpclient.SocketConnectionException: Channel handler removed before valid response has been received"));
        assertThat(closed.getMessage(), is("Channel handler removed before valid response has been received"));
    }

    @Test
    public void shouldNotNameAnyOtherFailure() {
        assertThat(reason(null, true), is(nullValue()));
        assertThat(reason(new SocketConnectionException("Channel handler removed before valid response has been received"), true), is(nullValue()));
        assertThat(reason(new ConnectException("something unexpected"), true), is(nullValue()));
        assertThat(reason(new IOException("Connection reset by peer"), true), is(nullValue()));
        assertThat(reason(new SSLHandshakeException("PKIX path building failed"), true), is(nullValue()));
        assertThat(reason(new RuntimeException((String) null), true), is(nullValue()));
    }

    @Test
    public void shouldTakeAForwardToTheReceivingAddressOrALoopbackAddressOnItsPortForTheReceivingAddress() {
        assertThat(targetsReceivingAddress("127.0.0.1", 1080, "127.0.0.1:1080"), is(true));
        assertThat(targetsReceivingAddress("localhost", 1080, "172.17.0.2:1080"), is(true));
        assertThat(targetsReceivingAddress("LOCALHOST", 1080, "127.0.0.1:1080"), is(true));
        assertThat(targetsReceivingAddress("0.0.0.0", 1080, "127.0.0.1:1080"), is(true));
        assertThat(targetsReceivingAddress("::1", 1080, "[0:0:0:0:0:0:0:1]:1080"), is(true));
        assertThat(targetsReceivingAddress("[::1]", 1080, "0:0:0:0:0:0:0:1:1080"), is(true));
        assertThat(targetsReceivingAddress("172.17.0.2", 1080, "172.17.0.2:1080"), is(true));
        assertThat(targetsReceivingAddress("mockserver.local", 1080, "mockserver.local:1080"), is(true));
    }

    @Test
    public void shouldNotTakeAnotherPortOrHostForTheReceivingAddress() {
        assertThat(targetsReceivingAddress("127.0.0.1", 1081, "127.0.0.1:1080"), is(false));
        assertThat(targetsReceivingAddress("upstream.example", 1080, "127.0.0.1:1080"), is(false));
        assertThat(targetsReceivingAddress("172.17.0.3", 1080, "172.17.0.2:1080"), is(false));
        assertThat(targetsReceivingAddress("127.0.0.1", 10800, "127.0.0.1:1080"), is(false));
        assertThat(targetsReceivingAddress("127.0.0.1", 1080, null), is(false));
        assertThat(targetsReceivingAddress(null, 1080, "127.0.0.1:1080"), is(false));
        assertThat(targetsReceivingAddress("127.0.0.1", null, "127.0.0.1:1080"), is(false));
        assertThat(targetsReceivingAddress("127.0.0.1", 1080, "no-port"), is(false));
    }
}
