package org.mockserver.authentication;

import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.model.HttpRequest;

import java.util.LinkedHashSet;
import java.util.Set;

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

    /**
     * Runs every delegate's {@link AuthenticationHandler#authenticate} (preserving the
     * AND semantics: any unauthenticated delegate fails the whole chain). When all
     * delegates authenticate, returns a combined result that takes the FIRST verified
     * principal that is not an mTLS client-certificate principal (today only OIDC supplies
     * one; the JWT handler supplies none), falling back to the verified mTLS principal, and
     * unions every delegate's scopes.
     */
    @Override
    public AuthenticationResult authenticate(HttpRequest request) {
        AuthenticationResult principalResult = null;
        Set<String> unionedScopes = new LinkedHashSet<>();
        for (AuthenticationHandler authenticationHandler : authenticationHandlers) {
            AuthenticationResult result = authenticationHandler.authenticate(request);
            if (!result.isAuthenticated()) {
                return AuthenticationResult.unauthenticated();
            }
            unionedScopes.addAll(result.getScopes());
            if (result.getPrincipal() != null && (principalResult == null || (isClientCertificatePrincipal(principalResult) && !isClientCertificatePrincipal(result)))) {
                principalResult = result;
            }
        }
        if (principalResult != null) {
            return AuthenticationResult.authenticated(
                principalResult.getPrincipal(),
                principalResult.getPrincipalSource(),
                principalResult.getClaims(),
                unionedScopes
            );
        }
        return AuthenticationResult.authenticated(null, "none", java.util.Map.of(), unionedScopes);
    }

    private static boolean isClientCertificatePrincipal(AuthenticationResult result) {
        return MTLSAuthenticationHandler.PRINCIPAL_SOURCE.equals(result.getPrincipalSource());
    }

}
