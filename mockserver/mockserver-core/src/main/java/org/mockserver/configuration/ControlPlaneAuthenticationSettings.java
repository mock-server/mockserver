package org.mockserver.configuration;

import org.mockserver.authentication.authorization.ControlPlaneRole;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An immutable, consistent view of a {@link Configuration}'s control-plane authentication and
 * authorization settings.
 *
 * <p>{@code PUT /mockserver/configuration} writes these settings one field at a time. A gate that read
 * the fields individually could see a mix of old and new values mid-{@code PUT}: switching from mTLS to
 * JWT writes {@code controlPlaneTLSMutualAuthenticationRequired=false} before
 * {@code controlPlaneJWTAuthenticationRequired=true}, so a request in between saw no authentication
 * required. The configuration therefore publishes this snapshot through one volatile reference, replaced
 * only once a whole update has been applied (see {@link AtomicConfigurationUpdate}). A reader takes one
 * snapshot with {@link #of(Configuration)} and makes its whole decision from it, so it sees either every
 * old value or every new one.
 *
 * <p>Values not set on the instance are resolved from {@link ConfigurationProperties}, exactly as the
 * {@link Configuration} getters resolve them, and re-resolved when a global property changes.
 */
public final class ControlPlaneAuthenticationSettings {

    static final long UNRESOLVED = Long.MIN_VALUE;

    static final ControlPlaneAuthenticationSettings UNSET = new ControlPlaneAuthenticationSettings(Values.UNSET, Values.UNSET, null, UNRESOLVED);

    /** the values as set on the {@link Configuration} instance, {@code null} where unset */
    final Values raw;
    private final Values resolved;
    // resolving the JVM-wide matching-claims default parses it and can throw; the getter rethrows, so a
    // malformed property fails at the same point it did before the snapshot existed (only when used)
    private final RuntimeException matchingClaimsFailure;
    /** the {@link ConfigurationProperties#modificationCount()} the unset values were resolved at */
    final long generation;
    private String signature;

    ControlPlaneAuthenticationSettings(Values raw, Values resolved, RuntimeException matchingClaimsFailure, long generation) {
        this.raw = raw;
        this.resolved = resolved;
        this.matchingClaimsFailure = matchingClaimsFailure;
        this.generation = generation;
    }

    /**
     * @return the configuration's current control-plane authentication settings, all from one update
     */
    public static ControlPlaneAuthenticationSettings of(Configuration configuration) {
        return configuration.controlPlaneAuthenticationSettings();
    }

    /**
     * @return {@code true} if any control-plane authentication mechanism is enabled
     */
    public boolean authenticationRequired() {
        return Boolean.TRUE.equals(resolved.tlsMutualAuthenticationRequired)
            || Boolean.TRUE.equals(resolved.jwtAuthenticationRequired)
            || Boolean.TRUE.equals(resolved.oidcAuthenticationRequired)
            || Boolean.TRUE.equals(resolved.basicAuthenticationRequired);
    }

    /**
     * A stable string capturing every value that feeds authentication handler construction, so a cached
     * handler is rebuilt exactly when one of them changes. Sorted collections keep it order-independent.
     * Computed once per snapshot, so a collection must be changed through its {@link Configuration}
     * setter, not mutated in place, for the change to be seen.
     */
    public String signature() {
        // racy single-check: the snapshot is immutable, so concurrent first calls compute the same String
        String memoised = signature;
        if (memoised == null) {
            memoised = computeSignature();
            signature = memoised;
        }
        return memoised;
    }

    private String computeSignature() {
        return new StringBuilder()
            .append(controlPlaneTLSMutualAuthenticationRequired()).append('|')
            .append(controlPlaneTLSMutualAuthenticationCAChain()).append('|')
            .append(controlPlaneJWTAuthenticationRequired()).append('|')
            .append(controlPlaneJWTAuthenticationJWKSource()).append('|')
            .append(controlPlaneJWTAuthenticationExpectedAudience()).append('|')
            .append(sorted(controlPlaneJWTAuthenticationMatchingClaims())).append('|')
            .append(sorted(controlPlaneJWTAuthenticationRequiredClaims())).append('|')
            .append(controlPlaneOidcAuthenticationRequired()).append('|')
            .append(controlPlaneOidcJwksUri()).append('|')
            .append(controlPlaneOidcIssuer()).append('|')
            .append(controlPlaneOidcAudience()).append('|')
            .append(controlPlaneOidcScopeClaim()).append('|')
            .append(sorted(controlPlaneOidcRequiredScopes())).append('|')
            .append(controlPlaneBasicAuthenticationRequired()).append('|')
            .append(digest(controlPlaneBasicAuthenticationCredentials())).append('|')
            .append(controlPlaneBasicAuthenticationUsersFile()).append('|')
            .append(controlPlaneBasicAuthenticationAllowInsecure())
            .toString();
    }

    // the signature is compared, never shown, but it still carries no password hash
    private static String digest(String value) {
        if (value == null) {
            return "null";
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException noSha256) {
            throw new IllegalStateException(noSha256);
        }
    }

    private static String sorted(Map<String, String> map) {
        return map == null ? "null" : new TreeMap<>(map).toString();
    }

    private static String sorted(Set<String> set) {
        return set == null ? "null" : new TreeSet<>(set).toString();
    }

    public Boolean controlPlaneTLSMutualAuthenticationRequired() {
        return resolved.tlsMutualAuthenticationRequired;
    }

    public String controlPlaneTLSMutualAuthenticationCAChain() {
        return resolved.tlsMutualAuthenticationCAChain;
    }

    public Boolean controlPlaneJWTAuthenticationRequired() {
        return resolved.jwtAuthenticationRequired;
    }

    public String controlPlaneJWTAuthenticationJWKSource() {
        return resolved.jwtAuthenticationJWKSource;
    }

    public String controlPlaneJWTAuthenticationExpectedAudience() {
        return resolved.jwtAuthenticationExpectedAudience;
    }

    public Map<String, String> controlPlaneJWTAuthenticationMatchingClaims() {
        if (matchingClaimsFailure != null) {
            throw matchingClaimsFailure;
        }
        return resolved.jwtAuthenticationMatchingClaims;
    }

    public Set<String> controlPlaneJWTAuthenticationRequiredClaims() {
        return resolved.jwtAuthenticationRequiredClaims;
    }

    public Boolean controlPlaneOidcAuthenticationRequired() {
        return resolved.oidcAuthenticationRequired;
    }

    public String controlPlaneOidcIssuer() {
        return resolved.oidcIssuer;
    }

    public String controlPlaneOidcJwksUri() {
        return resolved.oidcJwksUri;
    }

    public String controlPlaneOidcAudience() {
        return resolved.oidcAudience;
    }

    public Set<String> controlPlaneOidcRequiredScopes() {
        return resolved.oidcRequiredScopes;
    }

    public String controlPlaneOidcScopeClaim() {
        return resolved.oidcScopeClaim;
    }

    public Boolean controlPlaneAuthorizationEnabled() {
        return resolved.authorizationEnabled;
    }

    public Map<String, ControlPlaneRole> controlPlaneScopeMapping() {
        return resolved.scopeMapping;
    }

    public Boolean controlPlaneBasicAuthenticationRequired() {
        return resolved.basicAuthenticationRequired;
    }

    public String controlPlaneBasicAuthenticationCredentials() {
        return resolved.basicAuthenticationCredentials;
    }

    public String controlPlaneBasicAuthenticationUsersFile() {
        return resolved.basicAuthenticationUsersFile;
    }

    public Boolean controlPlaneBasicAuthenticationAllowInsecure() {
        return resolved.basicAuthenticationAllowInsecure;
    }

    static final class Values {

        static final Values UNSET = new Values(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        final Boolean tlsMutualAuthenticationRequired;
        final String tlsMutualAuthenticationCAChain;
        final Boolean jwtAuthenticationRequired;
        final String jwtAuthenticationJWKSource;
        final String jwtAuthenticationExpectedAudience;
        final Map<String, String> jwtAuthenticationMatchingClaims;
        final Set<String> jwtAuthenticationRequiredClaims;
        final Boolean oidcAuthenticationRequired;
        final String oidcIssuer;
        final String oidcJwksUri;
        final String oidcAudience;
        final Set<String> oidcRequiredScopes;
        final String oidcScopeClaim;
        final Boolean authorizationEnabled;
        final Map<String, ControlPlaneRole> scopeMapping;
        final Boolean basicAuthenticationRequired;
        final String basicAuthenticationCredentials;
        final String basicAuthenticationUsersFile;
        final Boolean basicAuthenticationAllowInsecure;

        @SuppressWarnings("java:S107")
        Values(Boolean tlsMutualAuthenticationRequired, String tlsMutualAuthenticationCAChain,
               Boolean jwtAuthenticationRequired, String jwtAuthenticationJWKSource, String jwtAuthenticationExpectedAudience,
               Map<String, String> jwtAuthenticationMatchingClaims, Set<String> jwtAuthenticationRequiredClaims,
               Boolean oidcAuthenticationRequired, String oidcIssuer, String oidcJwksUri, String oidcAudience,
               Set<String> oidcRequiredScopes, String oidcScopeClaim,
               Boolean authorizationEnabled, Map<String, ControlPlaneRole> scopeMapping,
               Boolean basicAuthenticationRequired, String basicAuthenticationCredentials,
               String basicAuthenticationUsersFile, Boolean basicAuthenticationAllowInsecure) {
            this.tlsMutualAuthenticationRequired = tlsMutualAuthenticationRequired;
            this.tlsMutualAuthenticationCAChain = tlsMutualAuthenticationCAChain;
            this.jwtAuthenticationRequired = jwtAuthenticationRequired;
            this.jwtAuthenticationJWKSource = jwtAuthenticationJWKSource;
            this.jwtAuthenticationExpectedAudience = jwtAuthenticationExpectedAudience;
            this.jwtAuthenticationMatchingClaims = jwtAuthenticationMatchingClaims;
            this.jwtAuthenticationRequiredClaims = jwtAuthenticationRequiredClaims;
            this.oidcAuthenticationRequired = oidcAuthenticationRequired;
            this.oidcIssuer = oidcIssuer;
            this.oidcJwksUri = oidcJwksUri;
            this.oidcAudience = oidcAudience;
            this.oidcRequiredScopes = oidcRequiredScopes;
            this.oidcScopeClaim = oidcScopeClaim;
            this.authorizationEnabled = authorizationEnabled;
            this.scopeMapping = scopeMapping;
            this.basicAuthenticationRequired = basicAuthenticationRequired;
            this.basicAuthenticationCredentials = basicAuthenticationCredentials;
            this.basicAuthenticationUsersFile = basicAuthenticationUsersFile;
            this.basicAuthenticationAllowInsecure = basicAuthenticationAllowInsecure;
        }
    }
}
