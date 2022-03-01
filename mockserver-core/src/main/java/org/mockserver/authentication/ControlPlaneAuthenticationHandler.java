package org.mockserver.authentication;

import org.mockserver.model.HttpRequest;

public interface ControlPlaneAuthenticationHandler {
    boolean controlPlaneRequestAuthenticated(HttpRequest request);
}