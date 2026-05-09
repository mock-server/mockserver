package org.mockserver.closurecallback.websocketclient;

/**
 * @author jamesdbloom
 */
public class WebSocketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WebSocketException(String message) {
        super(message);
    }

    public WebSocketException(String message, Throwable e) {
        super(message, e);
    }
}
