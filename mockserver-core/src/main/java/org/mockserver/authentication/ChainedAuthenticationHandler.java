package org.mockserver.authentication;

import org.mockserver.model.HttpRequest;

public class ChainedAuthenticationHandler implements AuthenticationHandler {

    private final AuthenticationHandler[] authenticationHandlers;

    public ChainedAuthenticationHandler(AuthenticationHandler... authenticationHandlers) {
        this.authenticationHandlers = authenticationHandlers;
    }

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        for (AuthenticationHandler authenticationHandler : authenticationHandlers) {
            if (!authenticationHandler.controlPlaneRequestAuthenticated(request)) {
                return false;
            }
        }
        return true;
    }

}
