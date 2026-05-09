package org.mockserver.httpclient;

/**
 * This exception indicates there was an error while trying to communicate over a socket
 *
 * @author jamesdbloom
 */
public class SocketConnectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SocketConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    SocketConnectionException(String message) {
        super(message);
    }
}
