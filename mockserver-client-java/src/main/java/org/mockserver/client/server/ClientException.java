package org.mockserver.client.server;

/**
 * @author jamesdbloom
 */
public class ClientException extends RuntimeException {

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientException(String message) {
        super(message);
    }
}
