package org.mockserver.netty.websocketregistry;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Decides whether a callback WebSocket upgrade may take over a client id that is already registered
 * to another connection, so a caller that learns a client id cannot silently receive that client's
 * callbacks and breakpoints.
 * <ul>
 *     <li>When both connections were authenticated by a handler that names its caller, the same caller
 *     takes over at once.</li>
 *     <li>Otherwise the takeover is allowed only when the registered connection is gone: it is no longer
 *     active, it closes, or it does not answer a WebSocket ping within {@link #LIVENESS_TIMEOUT_MILLIS}.
 *     When it answers, another named caller is refused as {@link Decision#REFUSE_OTHER_PRINCIPAL}, so a
 *     client whose credential changed (a renewed certificate) can still replace its own dead connection.</li>
 * </ul>
 */
final class CallbackClientIdClaim {

    /**
     * Long enough for a client that is reading to answer over a slow link, short enough that a client
     * reconnecting after a half-open drop waits only this long. A client that is not reading (for
     * example, running a slow callback on its only reader) does not answer and is treated as gone.
     */
    static final long LIVENESS_TIMEOUT_MILLIS = 2000;
    static final AttributeKey<String> PRINCIPAL_IDENTITY = AttributeKey.valueOf("CALLBACK_WEB_SOCKET_PRINCIPAL_IDENTITY");
    private static final AttributeKey<Promise<Void>> PENDING_PONG = AttributeKey.valueOf("CALLBACK_WEB_SOCKET_PENDING_PONG");

    enum Decision {
        ALLOW(false),
        ALLOW_AND_CLOSE_UNRESPONSIVE(true),
        REFUSE_OTHER_PRINCIPAL(false),
        REFUSE_REGISTERED_CONNECTION_ANSWERS(false);

        private final boolean closeRegistered;

        Decision(boolean closeRegistered) {
            this.closeRegistered = closeRegistered;
        }

        boolean allowed() {
            return this == ALLOW || this == ALLOW_AND_CLOSE_UNRESPONSIVE;
        }

        /**
         * @return whether the registered connection must be closed once the new one has taken the client id
         */
        boolean closeRegistered() {
            return closeRegistered;
        }
    }

    private CallbackClientIdClaim() {
    }

    /**
     * @param registered the connection the client id is registered to, or null
     * @param admission  the caller asking for the client id
     * @param executor   runs {@code then}: the new connection's event loop
     * @param then       given the decision exactly once, at once or after the liveness check
     */
    static void decide(Channel registered, ControlPlaneWebSocketUpgradeGate.Admission admission, EventExecutor executor, Consumer<Decision> then) {
        if (registered == null || !registered.isActive()) {
            then.accept(Decision.ALLOW);
            return;
        }
        String registeredIdentity = registered.attr(PRINCIPAL_IDENTITY).get();
        boolean bothNamed = admission.authenticated() && admission.principalIdentity() != null && registeredIdentity != null;
        if (bothNamed && registeredIdentity.equals(admission.principalIdentity())) {
            then.accept(Decision.ALLOW);
            return;
        }
        checkLiveness(registered, executor, bothNamed ? Decision.REFUSE_OTHER_PRINCIPAL : Decision.REFUSE_REGISTERED_CONNECTION_ANSWERS, then);
    }

    private static void checkLiveness(Channel registered, EventExecutor executor, Decision whenAnswered, Consumer<Decision> then) {
        AtomicBoolean decided = new AtomicBoolean();
        Promise<Void> pong = registered.eventLoop().newPromise();
        Promise<Void> pending = registered.attr(PENDING_PONG).setIfAbsent(pong);
        Promise<Void> awaitedPong = pending != null ? pending : pong;
        AtomicReference<Runnable> removeListeners = new AtomicReference<>(() -> {
        });
        Consumer<Decision> decide = decision -> {
            if (decided.compareAndSet(false, true)) {
                removeListeners.get().run();
                if (executor.inEventLoop()) {
                    then.accept(decision);
                } else {
                    executor.execute(() -> then.accept(decision));
                }
            }
        };
        ScheduledFuture<?> timeout = executor.schedule(() -> decide.accept(Decision.ALLOW_AND_CLOSE_UNRESPONSIVE), LIVENESS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        GenericFutureListener<Future<? super Void>> onPong = future -> decide.accept(whenAnswered);
        ChannelFutureListener onClose = future -> decide.accept(Decision.ALLOW);
        removeListeners.set(() -> {
            timeout.cancel(false);
            awaitedPong.removeListener(onPong);
            registered.closeFuture().removeListener(onClose);
        });
        awaitedPong.addListener(onPong);
        registered.closeFuture().addListener(onClose);
        registered.writeAndFlush(new PingWebSocketFrame(Unpooled.EMPTY_BUFFER)).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                future.channel().close();
            }
        });
    }

    /**
     * Records that a callback connection answered a ping.
     */
    static void pongReceived(Channel channel) {
        Promise<Void> pending = channel.attr(PENDING_PONG).getAndSet(null);
        if (pending != null) {
            pending.trySuccess(null);
        }
    }
}
