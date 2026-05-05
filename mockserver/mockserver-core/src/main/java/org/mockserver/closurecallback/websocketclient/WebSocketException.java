package org.mockserver.closurecallback.websocketclient;

/**
 * @author jamesdbloom
 */
public class WebSocketException extends RuntimeException {
    public WebSocketException(String message) {
        super(message);
    }

    public WebSocketException(String message, Throwable e) {
        super(message, e);
    }
}
