package org.mockserver.extension;

/**
 * An {@link Extension} told about each request the server has answered (WireMock's {@code ServeEventListener}
 * after completion): a mocked response, a forwarded response or the not-matched response.
 * <p>
 * Events are taken from the event log, after bodies are truncated to {@code mockserver.maxLoggedBodyBytes}, so an
 * entry the event log drops is not reported, and load-injection traffic is never reported. Headers are raw,
 * including {@code Authorization}.
 * <p>
 * {@link #onServed} runs on the extension's own listener thread. Each listener's queue is bounded by count
 * ({@code mockserver.extensionListenerQueueSize}) and by its share of {@code mockserver.extensionListenerMaxBytes};
 * events that do not fit are dropped and counted in {@code mock_server_extension_listener_dropped_total}. A drop
 * never affects the event log or verification.
 */
public interface ServeEventListener extends Extension {

    /**
     * @param event the request and the response the server sent
     */
    void onServed(ServeEvent event);
}
