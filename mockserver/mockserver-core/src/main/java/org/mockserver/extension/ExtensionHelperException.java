package org.mockserver.extension;

/**
 * Thrown into a template render when an extension template helper cannot be called, throws, or does not return in
 * time. The template fails through the usual template-failure path.
 */
public class ExtensionHelperException extends RuntimeException {

    public ExtensionHelperException(String message) {
        super(message);
    }

    public ExtensionHelperException(String message, Throwable cause) {
        super(message, cause);
    }
}
