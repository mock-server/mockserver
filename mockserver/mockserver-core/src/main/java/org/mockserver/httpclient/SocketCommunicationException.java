package org.mockserver.httpclient;

/**
 * This exception indicates there was an error while trying to communicate over a socket
 *
 * @author jamesdbloom
 */
public class SocketCommunicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SocketCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
