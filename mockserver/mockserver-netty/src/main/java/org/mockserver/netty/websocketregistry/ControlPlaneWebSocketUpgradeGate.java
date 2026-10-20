package org.mockserver.netty.websocketregistry;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.mockserver.configuration.Configuration;
import org.mockserver.cors.CORSOriginPolicy;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.tls.SniHandler;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static org.mockserver.formatting.StringFormatter.escapeControlCharactersForLog;
import static org.mockserver.log.model.LogEntry.LogMessageType.AUTHENTICATION_FAILED;

/**
 * The checks every control-plane WebSocket upgrade ({@code /_mockserver_ui_websocket} and
 * {@code /_mockserver_callback_websocket}) must pass before it is upgraded: the browser
 * {@code Origin} must be allowed by {@link CORSOriginPolicy}, and the control-plane
 * authentication, when configured, must allow the request. A rejected upgrade gets a
 * {@code 403} (origin or role) or {@code 401} (credentials) and the connection is closed.
 */
public final class ControlPlaneWebSocketUpgradeGate {

    private ControlPlaneWebSocketUpgradeGate() {
    }

    /**
     * @param description names the socket in the rejection log, e.g. "dashboard UI"
     * @return true when the upgrade may proceed; false once the rejection has been written
     */
    public static boolean allowUpgrade(ChannelHandlerContext ctx, FullHttpRequest httpRequest, HttpState httpState, MockServerLogger mockServerLogger, boolean sslEnabledUpstream, String description) {
        return admitUpgrade(ctx, httpRequest, httpState, mockServerLogger, sslEnabledUpstream, description) != null;
    }

    /**
     * @param description names the socket in the rejection log, e.g. "dashboard UI"
     * @return the admitted caller when the upgrade may proceed; null once the rejection has been written
     */
    public static Admission admitUpgrade(ChannelHandlerContext ctx, FullHttpRequest httpRequest, HttpState httpState, MockServerLogger mockServerLogger, boolean sslEnabledUpstream, String description) {
        if (!originAllowed(ctx, httpRequest, httpState, mockServerLogger, description)) {
            return null;
        }
        return authenticated(ctx, httpRequest, httpState, mockServerLogger, sslEnabledUpstream, description);
    }

    /**
     * The caller of an admitted upgrade: whether control-plane authentication admitted it and, when
     * its authentication handler can name one, the caller's identity.
     */
    public static final class Admission {
        private static final Admission UNAUTHENTICATED = new Admission(false, null);
        private final boolean authenticated;
        private final String principalIdentity;

        Admission(boolean authenticated, String principalIdentity) {
            this.authenticated = authenticated;
            this.principalIdentity = principalIdentity;
        }

        public boolean authenticated() {
            return authenticated;
        }

        public String principalIdentity() {
            return principalIdentity;
        }
    }

    private static boolean originAllowed(ChannelHandlerContext ctx, FullHttpRequest httpRequest, HttpState httpState, MockServerLogger mockServerLogger, String description) {
        String origin = httpRequest.headers().get(HttpHeaderNames.ORIGIN);
        Configuration configuration = httpState.getConfiguration();
        if (CORSOriginPolicy.isControlPlaneOriginAllowed(configuration, origin, httpRequest.headers().getAll(HttpHeaderNames.HOST))) {
            return true;
        }
        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("{} web socket upgrade rejected with status 403 - origin {} is not allowed, set controlPlaneCorsAllowOrigin to allow it (a page MockServer serves itself at a host name that is not rebinding-safe can instead be allowed with controlPlaneTrustedHostSuffixes)")
                    .setArguments(description, escapeControlCharactersForLog(origin))
            );
        }
        reject(ctx, HttpResponseStatus.FORBIDDEN);
        return false;
    }

    private static Admission authenticated(ChannelHandlerContext ctx, FullHttpRequest httpRequest, HttpState httpState, MockServerLogger mockServerLogger, boolean sslEnabledUpstream, String description) {
        if (httpState.getControlPlaneAuthenticationHandler() == null) {
            return Admission.UNAUTHENTICATED;
        }
        HttpRequest mockServerRequest = new FullHttpRequestToMockServerHttpRequest(
            httpState.getConfiguration(),
            mockServerLogger,
            sslEnabledUpstream,
            SniHandler.retrieveClientCertificates(mockServerLogger, ctx),
            ctx.channel().localAddress() instanceof InetSocketAddress
                ? ((InetSocketAddress) ctx.channel().localAddress()).getPort()
                : null
        ).mapFullHttpRequestToMockServerRequest(
            httpRequest,
            null,
            ctx.channel().localAddress(),
            ctx.channel().remoteAddress(),
            SniHandler.getALPNProtocol(mockServerLogger, ctx)
        );
        HttpState.ControlPlaneAuthDecision decision = httpState.evaluateControlPlaneAuthentication(mockServerRequest);
        if (decision.isAllowed()) {
            return decision.authenticated() ? new Admission(true, decision.principalIdentity()) : Admission.UNAUTHENTICATED;
        }
        HttpResponseStatus status = decision.outcome() == HttpState.ControlPlaneAuthOutcome.FORBIDDEN
            ? HttpResponseStatus.FORBIDDEN
            : HttpResponseStatus.UNAUTHORIZED;
        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(AUTHENTICATION_FAILED)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("{} web socket upgrade rejected with status {} - control plane authentication required")
                    .setArguments(description, status.code())
            );
        }
        reject(ctx, status);
        return null;
    }

    static void reject(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
        response.headers().set(CONTENT_LENGTH, 0);
        ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
