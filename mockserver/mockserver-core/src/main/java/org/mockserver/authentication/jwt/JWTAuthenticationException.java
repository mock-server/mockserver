package org.mockserver.authentication.jwt;

import org.mockserver.authentication.AuthenticationException;

public class JWTAuthenticationException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public JWTAuthenticationException(String message) {
        super(message);
    }

    public JWTAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

}
