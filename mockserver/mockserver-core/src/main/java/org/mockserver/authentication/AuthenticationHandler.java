package org.mockserver.authentication;

import org.mockserver.model.HttpRequest;

public interface AuthenticationHandler {
    boolean controlPlaneRequestAuthenticated(HttpRequest request);
}