package org.mockserver.mockserver.callback.client;

/**
 * @author jamesdbloom
 */
public class WebSocketClientException extends RuntimeException {
    public WebSocketClientException(Exception e) {
        super(e);
    }
}
