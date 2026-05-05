package org.mockserver.client;

/**
 * @author jamesdbloom
 */
public class ClientException extends RuntimeException {

    ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    ClientException(String message) {
        super(message);
    }

}
