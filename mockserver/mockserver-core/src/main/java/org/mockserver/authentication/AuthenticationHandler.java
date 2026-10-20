package org.mockserver.authentication;

import org.mockserver.model.HttpRequest;

import java.util.Map;
import java.util.Set;

/**
 * <strong>Implementations delegate in BOTH directions — check which way before editing one.</strong>
 * {@code JWTAuthenticationHandler} and
 * {@code ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler} implement only
 * {@link #controlPlaneRequestAuthenticated} and inherit the default {@link #authenticate}, so the boolean
 * method holds their logic. {@code OidcAuthenticationHandler} and {@code MTLSAuthenticationHandler} invert
 * this: they override {@link #authenticate} (to surface a verified principal) and their
 * {@link #controlPlaneRequestAuthenticated} is a one-line delegation back to it.
 * <p>
 * The enforcement point ({@code HttpState.evaluateControlPlaneAuthentication}) calls {@link #authenticate},
 * so for OIDC and mTLS the boolean method is not on the enforcement path. It carries no independent logic, so
 * hardening {@link #authenticate} covers it — but an edit made only to the boolean method of a handler that
 * overrides {@link #authenticate} will have no effect.
 */
public interface AuthenticationHandler {

    /**
     * Legacy boolean SPI: returns true if the control-plane request is authenticated.
     * Implementations may throw {@link AuthenticationException} to signal a 401 with a
     * specific reason. Existing and third-party handlers implement only this method.
     */
    boolean controlPlaneRequestAuthenticated(HttpRequest request);

    /**
     * Richer authentication that additionally surfaces the VERIFIED principal, the
     * source of verification, and a redaction-safe subset of claims/scopes for audit.
     * <p>
     * Default-adapts the legacy {@link #controlPlaneRequestAuthenticated} so existing
     * handlers need ZERO changes: a true outcome becomes an authenticated-but-anonymous
     * result (principal null, source "none"), preserving byte-for-byte behaviour. May
     * throw {@link AuthenticationException} (401) exactly as the boolean method does.
     */
    default AuthenticationResult authenticate(HttpRequest request) {
        return controlPlaneRequestAuthenticated(request)
            ? AuthenticationResult.authenticated(null, "none", Map.of(), Set.of())
            : AuthenticationResult.unauthenticated();
    }
}
