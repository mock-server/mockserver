package org.mockserver.authentication.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.BoundedResourceRetriever;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.file.FilePath;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.url.URLParser;
import org.slf4j.event.Level;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class JWTAuthenticationHandler implements AuthenticationHandler {

    private final MockServerLogger mockServerLogger;
    private Throwable jwtValidatorInitialisationException;
    private JWTValidator jwtValidator;

    public JWTAuthenticationHandler(MockServerLogger mockServerLogger, String jwkSource) {
        this.mockServerLogger = mockServerLogger;
        try {
            if (URLParser.isFullUrl(jwkSource)) {
                this.jwtValidator = new JWTValidator(new RemoteJWKSet<>(URI.create(jwkSource).toURL(), BoundedResourceRetriever.create()));
            } else {
                this.jwtValidator = new JWTValidator(new ImmutableJWKSet<>(JWKSet.load(new File(FilePath.absolutePathFromClassPathOrPath(jwkSource)))));
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception building JWT validator for:{}")
                    .setArguments(jwkSource)
                    .setThrowable(throwable)
            );
            this.jwtValidatorInitialisationException = throwable;
        }
    }

    public JWTAuthenticationHandler withExpectedAudience(String expectedAudience) {
        jwtValidator.withExpectedAudience(expectedAudience);
        return this;
    }

    public JWTAuthenticationHandler withMatchingClaims(Map<String, String> matchingClaims) {
        jwtValidator.withMatchingClaims(matchingClaims);
        return this;
    }

    public JWTAuthenticationHandler withRequiredClaims(Set<String> requiredClaims) {
        jwtValidator.withRequiredClaims(requiredClaims);
        return this;
    }

    public static final String VERIFIED_ISSUER_CLAIM = "verifiedJwtIssuer";
    public static final String VERIFIED_SUBJECT_CLAIM = "verifiedJwtSubject";

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        return validatedClaims(request) != null;
    }

    /**
     * Authenticates as {@link #controlPlaneRequestAuthenticated} does, carrying the validated token's issuer and
     * subject as {@value #VERIFIED_ISSUER_CLAIM} and {@value #VERIFIED_SUBJECT_CLAIM}. No principal is set, so the
     * audit record is unchanged.
     */
    @Override
    public AuthenticationResult authenticate(HttpRequest request) {
        JWTClaimsSet claims = validatedClaims(request);
        if (claims == null) {
            return AuthenticationResult.unauthenticated();
        }
        Map<String, Object> verified = new LinkedHashMap<>();
        if (claims.getIssuer() != null) {
            verified.put(VERIFIED_ISSUER_CLAIM, claims.getIssuer());
        }
        if (claims.getSubject() != null) {
            verified.put(VERIFIED_SUBJECT_CLAIM, claims.getSubject());
        }
        return AuthenticationResult.authenticated(null, "none", verified, Set.of());
    }

    /**
     * The issuer and subject of the validated bearer token, from {@code result} when this handler produced it,
     * otherwise from authenticating {@code request} again; null when it does not authenticate or has no subject.
     */
    @Override
    public String principalIdentity(HttpRequest request, AuthenticationResult result) {
        Map<String, Object> claims = result != null && result.getClaims().containsKey(VERIFIED_SUBJECT_CLAIM) ? result.getClaims() : null;
        if (claims == null && request != null) {
            try {
                claims = authenticate(request).getClaims();
            } catch (AuthenticationException notAuthenticated) {
                return null;
            }
        }
        Object subject = claims != null ? claims.get(VERIFIED_SUBJECT_CLAIM) : null;
        return subject != null ? "jwt:" + claims.get(VERIFIED_ISSUER_CLAIM) + "|" + subject : null;
    }

    private JWTClaimsSet validatedClaims(HttpRequest request) {
        if (jwtValidator == null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("JWT control plane request failed authentication because JWT validator is not initialised:{}")
                    .setArguments(request)
                    .setThrowable(jwtValidatorInitialisationException)
            );
        } else {
            List<String> authorizationHeaders = request.getHeader(AUTHORIZATION.toString());
            if (authorizationHeaders.isEmpty()) {
                logAuthorisationFailure(request, "no authorization header found");
            }
            for (String authorizationHeader : authorizationHeaders) {
                int idx = authorizationHeader.indexOf(' ');
                if (idx <= 0) {
                    logAuthorisationFailure(request, "authorization header is invalid format");
                } else {
                    String headerPrefix = authorizationHeader.substring(0, idx);
                    if (isBlank(headerPrefix)) {
                        logAuthorisationFailure(request, "authorization type must be specified for authorization header");
                    } else if ("Bearer".equalsIgnoreCase(headerPrefix)) {
                        return jwtValidator.validate(authorizationHeader.substring(idx + 1));
                    } else {
                        logAuthorisationFailure(request, "only \"Bearer\" supported for authorization header");
                    }
                }
            }
        }
        return null;
    }

    private void logAuthorisationFailure(HttpRequest request, String failureReason) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(request)
                .setMessageFormat("JWT control plane request failed:{}for request:{}")
                .setArguments(failureReason, request)
        );
        throw new AuthenticationException(failureReason);
    }

}
