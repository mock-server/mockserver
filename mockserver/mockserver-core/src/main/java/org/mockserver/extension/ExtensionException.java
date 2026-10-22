package org.mockserver.extension;

/**
 * Thrown when an extension cannot be loaded, started or notified, which refuses or ends server start-up.
 */
public class ExtensionException extends RuntimeException {

    public ExtensionException(String message) {
        super(message);
    }

    public ExtensionException(String message, Throwable cause) {
        super(message, cause);
    }
}
