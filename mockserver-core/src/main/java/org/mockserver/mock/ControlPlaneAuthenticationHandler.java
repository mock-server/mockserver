package org.mockserver.mock;

import org.mockserver.model.HttpRequest;

public interface ControlPlaneAuthenticationHandler {
    boolean controlPlaneRequestAuthenticated(HttpRequest request);
}