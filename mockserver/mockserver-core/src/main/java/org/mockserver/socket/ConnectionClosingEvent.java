package org.mockserver.socket;

/**
 * User event fired from the head of a connection's pipeline, on its event loop, when {@link ConnectionClosing#mark}
 * first records that the connection will close: handlers that would otherwise go on releasing or reading work for it
 * learn at once, even when nothing else passes them before it closes.
 */
public final class ConnectionClosingEvent {

    public static final ConnectionClosingEvent INSTANCE = new ConnectionClosingEvent();

    private ConnectionClosingEvent() {
    }

    @Override
    public String toString() {
        return "ConnectionClosingEvent";
    }
}
