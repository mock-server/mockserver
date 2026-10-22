package org.mockserver.authentication;

import org.mockserver.authentication.basic.BasicAuthenticationHandler;
import org.mockserver.model.HttpRequest;

/**
 * Accepts a control-plane request that either control-plane HTTP Basic credentials or the other configured
 * mechanisms (mTLS, JWT, OIDC, still all required together) authenticate.
 * <p>
 * A request carrying {@code Authorization: Basic} is tried with Basic first, any other request with the other
 * mechanisms first, and when neither authenticates the first one's failure is reported: so a client using one
 * method sees that method's reason, not "no basic authorization header found".
 */
public class AnyOfAuthenticationHandler implements AuthenticationHandler {

    private final BasicAuthenticationHandler basic;
    private final AuthenticationHandler others;

    public AnyOfAuthenticationHandler(BasicAuthenticationHandler basic, AuthenticationHandler others) {
        this.basic = basic;
        this.others = others;
    }

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        return authenticate(request).isAuthenticated();
    }

    @Override
    public AuthenticationResult authenticate(HttpRequest request) {
        boolean basicFirst = BasicAuthenticationHandler.hasBasicAuthorization(request);
        AuthenticationHandler first = basicFirst ? basic : others;
        AuthenticationHandler second = basicFirst ? others : basic;
        AuthenticationException firstFailure = null;
        try {
            AuthenticationResult result = first.authenticate(request);
            if (result.isAuthenticated()) {
                return result;
            }
        } catch (AuthenticationException failure) {
            firstFailure = failure;
        }
        try {
            AuthenticationResult result = second.authenticate(request);
            if (result.isAuthenticated()) {
                return result;
            }
        } catch (AuthenticationException ignored) {
            // the first mechanism's reason is the one that names what the client tried
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return AuthenticationResult.unauthenticated();
    }

    @Override
    public String principalIdentity(HttpRequest request, AuthenticationResult result) {
        if (result != null && BasicAuthenticationHandler.PRINCIPAL_SOURCE.equals(result.getPrincipalSource())) {
            return basic.principalIdentity(request, result);
        }
        return others.principalIdentity(request, result);
    }
}
