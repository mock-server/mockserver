package org.mockserver.httpclient;

import io.netty.channel.ConnectTimeoutException;

import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;

/**
 * Why a forward's connection to its upstream could not be made, or was lost before the response, with what to change,
 * for the body of the forward's {@code 502}. Only called once a forward has failed. The text never names the upstream's
 * host or address: the request and its log entry already say where it was sent.
 */
public final class UpstreamConnectFailure {

    static final String UNRESOLVED = "upstream host name could not be resolved: check it is spelled correctly and resolves where MockServer runs (its DNS, hosts file or container network)";
    static final String REFUSED = "upstream refused the connection: check the upstream is running and listening on the port forwarded to";
    static final String TIMED_OUT = "connection to the upstream timed out: check the upstream is reachable from where MockServer runs (firewall, routing, VPN or an upstream proxy it must go through)";
    static final String TIMED_OUT_CONFIGURABLE = TIMED_OUT + ", or raise socketConnectionTimeoutInMillis if it is slow to accept connections";
    static final String UNREACHABLE = "upstream address could not be reached (no route to it): check the address, and the network, VPN or upstream proxy MockServer needs to reach it";
    public static final String OUT_OF_LOCAL_RESOURCES = "MockServer ran out of local ports or open files to connect to the upstream: a forward whose target is MockServer itself repeats until this happens, so check the forward's host and port; otherwise lower the number of concurrent forwards or raise the open-file limit";
    public static final String FORWARD_LOOP = "the forward's target is the address MockServer received the request on, so the request may have been forwarded back to MockServer itself, where it can match the same expectation again and loop: check the forward's host and port";

    private enum Cause {
        UNRESOLVED("its host name could not be resolved"),
        REFUSED("it refused the connection"),
        TIMED_OUT("the connection timed out"),
        UNREACHABLE("there is no route to it"),
        OUT_OF_LOCAL_RESOURCES("MockServer ran out of local ports or open files");

        private final String forProxy;

        Cause(String forProxy) {
            this.forProxy = forProxy;
        }
    }

    private UpstreamConnectFailure() {
    }

    /**
     * @param failure                    a failed forward, as it reached the action handler
     * @param connectTimeoutConfigurable whether the connect timeout is {@code socketConnectionTimeoutInMillis}, so the
     *                                   fix for a timeout can name it
     * @return the cause and what to change, or null when {@code failure} is not a connection that could not be made or
     * was lost before the response
     */
    public static String reason(Throwable failure, boolean connectTimeoutConfigurable) {
        if (failure == null) {
            return null;
        }
        UpstreamProxyUnreachableException proxy = UpstreamProxyUnreachableException.in(failure);
        if (proxy != null) {
            Cause cause = cause(proxy.getCause());
            return "upstream proxy set by " + proxy.getProperty() + " could not be reached (" + (cause != null ? cause.forProxy : "the connection failed") + "): check the proxy is running and its host and port are correct";
        }
        PooledConnectionClosedException pooled = PooledConnectionClosedException.in(failure);
        if (pooled != null) {
            return "upstream closed the pooled keep-alive connection the request was sent on without answering, and a " + pooled.getMethod() + " is not sent again in case the upstream acted on it: if the upstream closes idle connections, set forwardConnectionPoolIdleTimeoutMillis below its keep-alive timeout or set forwardConnectionPoolEnabled to false; otherwise the upstream may have closed the connection to reject the request";
        }
        Cause cause = cause(failure);
        if (cause == null) {
            return null;
        }
        switch (cause) {
            case UNRESOLVED:
                return UNRESOLVED;
            case REFUSED:
                return REFUSED;
            case TIMED_OUT:
                return connectTimeoutConfigurable ? TIMED_OUT_CONFIGURABLE : TIMED_OUT;
            case UNREACHABLE:
                return UNREACHABLE;
            default:
                return OUT_OF_LOCAL_RESOURCES;
        }
    }

    /**
     * Whether a forward to {@code targetHost}:{@code targetPort} goes back to the address MockServer received the
     * request on, {@code localAddress} as a request records it ({@code 127.0.0.1:1080}). A loopback target counts as the
     * receiving address whatever the request arrived on, since MockServer listens on the port on every interface by
     * default. Names are compared as text: nothing is looked up.
     */
    public static boolean targetsReceivingAddress(String targetHost, Integer targetPort, String localAddress) {
        if (targetHost == null || targetPort == null || localAddress == null) {
            return false;
        }
        int portSeparator = localAddress.lastIndexOf(':');
        if (portSeparator <= 0 || !localAddress.substring(portSeparator + 1).equals(String.valueOf(targetPort))) {
            return false;
        }
        String target = withoutBrackets(targetHost);
        return isLoopbackOrAny(target) || target.equalsIgnoreCase(withoutBrackets(localAddress.substring(0, portSeparator)));
    }

    private static String withoutBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static boolean isLoopbackOrAny(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost") || lower.startsWith("127.") || lower.equals("::1") || lower.equals("0:0:0:0:0:0:0:1") || lower.equals("0.0.0.0");
    }

    private static Cause cause(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            Cause cause = causeOf(failure);
            if (cause != null) {
                return cause;
            }
        }
        return null;
    }

    private static Cause causeOf(Throwable failure) {
        if (failure instanceof UnknownHostException || failure instanceof UnresolvedAddressException) {
            return Cause.UNRESOLVED;
        }
        if (failure instanceof ConnectTimeoutException) {
            return Cause.TIMED_OUT;
        }
        if (failure instanceof NoRouteToHostException) {
            return Cause.UNREACHABLE;
        }
        String message = failure.getMessage() != null ? failure.getMessage().toLowerCase(Locale.ROOT) : "";
        // any type: on epoll a channel that cannot be created is a ChannelException over Netty's NativeIoException
        if (failure instanceof BindException || message.contains("too many open files") || message.contains("assign requested address")) {
            return Cause.OUT_OF_LOCAL_RESOURCES;
        }
        if (failure instanceof SocketException) {
            if (message.contains("connection refused")) {
                return Cause.REFUSED;
            }
            if (message.contains("unreachable") || message.contains("no route to host")) {
                return Cause.UNREACHABLE;
            }
            // only a connect's: an established connection's retransmissions can time out too
            if (failure instanceof ConnectException && message.contains("timed out")) {
                return Cause.TIMED_OUT;
            }
        }
        return null;
    }
}
