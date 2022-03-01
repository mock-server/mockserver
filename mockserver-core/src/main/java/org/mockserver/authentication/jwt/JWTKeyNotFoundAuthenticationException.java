package org.mockserver.authentication.jwt;

import org.mockserver.authentication.AuthenticationException;

public class JWTKeyNotFoundAuthenticationException extends AuthenticationException {

    public JWTKeyNotFoundAuthenticationException(String message) {
        super(message);
    }

    public JWTKeyNotFoundAuthenticationException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
