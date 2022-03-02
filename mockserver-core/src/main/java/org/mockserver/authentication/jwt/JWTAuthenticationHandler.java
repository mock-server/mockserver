package org.mockserver.authentication.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.url.URLParser;
import org.slf4j.event.Level;

import java.io.File;
import java.net.URL;
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
                this.jwtValidator = new JWTValidator(new RemoteJWKSet<>(new URL(jwkSource)));
            } else {
                this.jwtValidator = new JWTValidator(new ImmutableJWKSet<>(JWKSet.load(new File(FileReader.absolutePathFromClassPathOrPath(jwkSource)))));
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

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
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
                        jwtValidator.validate(authorizationHeader.substring(idx + 1));
                        return true;
                    } else {
                        logAuthorisationFailure(request, "only \"Bearer\" supported for authorization header");
                    }
                }
            }
        }
        return false;
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
