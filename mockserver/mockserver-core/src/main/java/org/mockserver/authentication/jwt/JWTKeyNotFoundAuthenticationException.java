package org.mockserver.authentication.jwt;

import org.mockserver.authentication.AuthenticationException;

public class JWTKeyNotFoundAuthenticationException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public JWTKeyNotFoundAuthenticationException(String message) {
        super(message);
    }

    public JWTKeyNotFoundAuthenticationException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
