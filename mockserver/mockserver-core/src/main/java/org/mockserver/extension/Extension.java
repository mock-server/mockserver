package org.mockserver.extension;

import java.util.List;

/**
 * A server extension: trusted Java code loaded once, when a MockServer starts, from the class names in
 * {@code mockserver.extensions}, from {@code META-INF/services/org.mockserver.extension.Extension} entries when
 * {@code mockserver.extensionScanning} is {@code true}, or from {@code Configuration.extensions(...)} when
 * MockServer is embedded. Extensions cannot be added, removed or reconfigured over the control plane.
 * <p>
 * A class loaded by name needs a public no-argument constructor. Each server gets its own instance.
 * <p>
 * An extension that also implements {@link org.mockserver.server.initialize.ExpectationInitializer} has its
 * expectations loaded when the server starts, after the configured initialisers.
 */
public interface Extension {

    /**
     * @return a name unique among the extensions a server loads, made only of lower-case letters, digits and
     * {@code -}
     */
    String name();

    /**
     * @return the start order: lower values start first and stop last; ties are broken by {@link #name()}
     */
    default int priority() {
        return 0;
    }

    /**
     * Called once, before the server builds anything else. A throw, or not returning within
     * {@code mockserver.extensionStartTimeoutMillis}, refuses start-up, and the extensions already started are
     * stopped in reverse order.
     *
     * @param context the server's configuration and logger
     * @throws Exception to refuse start-up
     */
    default void start(ExtensionContext context) throws Exception {
    }

    /**
     * Called once after every port is bound (with an empty list in a WAR). The readiness endpoint
     * {@code /mockserver/ready} stays not-ready until every extension's {@code onStarted} returns. A throw, or not
     * returning within {@code mockserver.extensionStartTimeoutMillis}, stops the server.
     *
     * @param ports the ports the server is listening on
     * @throws Exception to stop the server
     */
    default void onStarted(List<Integer> ports) throws Exception {
    }

    /**
     * Called once when the server stops, or when start-up fails after this extension started, in reverse start
     * order. A throw is logged and does not stop the other extensions stopping.
     */
    default void stop() {
    }
}
