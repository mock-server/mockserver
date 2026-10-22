package org.mockserver.authentication.basic;

import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.mockserver.formatting.StringFormatter.escapeControlCharactersForLog;

/**
 * Authenticates a control-plane request by its {@code Authorization: Basic} credentials against the configured
 * {@link BasicAuthenticationCredentials}.
 * <ul>
 *     <li>Credentials on a connection without TLS are refused unless {@code allowInsecure} (tests only).</li>
 *     <li>Passwords are checked against bcrypt, argon2 or PBKDF2 hashes ({@link PasswordHash}); an unknown user is
 *     checked against another user's hash so it takes as long as a wrong password.</li>
 *     <li>Failures are throttled per source address ({@link BasicAuthenticationThrottle}) and logged at most once
 *     every {@value #FAILURE_LOG_INTERVAL_SECONDS} seconds.</li>
 *     <li>A successful check is remembered, as an HMAC under a key made at construction, so a client's later
 *     requests do not pay for the slow hash again.</li>
 * </ul>
 * The result's principal is the user name, its source {@value #PRINCIPAL_SOURCE}, and its scopes the user's
 * configured scopes, which {@code controlPlaneScopeMapping} maps to roles.
 */
public class BasicAuthenticationHandler implements AuthenticationHandler {

    public static final String PRINCIPAL_SOURCE = "verified-basic";
    public static final String USERNAME_CLAIM = "username";
    static final int FAILURE_LOG_INTERVAL_SECONDS = 10;
    static final int MAX_REMEMBERED_CREDENTIALS = 256;
    private static final String BASIC_PREFIX = "basic ";
    // requests whose transport records no peer share one record: never unthrottled, and a credential already verified
    // is admitted through a block, so the shared record cannot lock out users who logged in
    static final String UNKNOWN_SOURCE = "unknown";

    private final MockServerLogger mockServerLogger;
    private final BasicAuthenticationCredentials credentials;
    private final boolean allowInsecure;
    private final BasicAuthenticationThrottle throttle;
    private final LongSupplier clockMillis;
    private final List<PasswordHash> costRepresentatives;
    private final byte[] rememberKey = new byte[32];
    private final Map<String, String> remembered = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_REMEMBERED_CREDENTIALS;
        }
    };
    private final AtomicLong lastFailureLogMillis = new AtomicLong(Long.MIN_VALUE);
    private final AtomicInteger unloggedFailures = new AtomicInteger();

    public BasicAuthenticationHandler(MockServerLogger mockServerLogger, BasicAuthenticationCredentials credentials, boolean allowInsecure) {
        this(mockServerLogger, credentials, allowInsecure, System::currentTimeMillis);
    }

    BasicAuthenticationHandler(MockServerLogger mockServerLogger, BasicAuthenticationCredentials credentials, boolean allowInsecure, LongSupplier clockMillis) {
        this.mockServerLogger = mockServerLogger;
        this.credentials = credentials;
        this.allowInsecure = allowInsecure;
        this.clockMillis = clockMillis;
        this.throttle = new BasicAuthenticationThrottle(clockMillis);
        Map<String, PasswordHash> byCost = new LinkedHashMap<>();
        for (String name : credentials.usernames()) {
            PasswordHash hash = credentials.user(name).passwordHash();
            byCost.putIfAbsent(hash.costKey(), hash);
        }
        this.costRepresentatives = List.copyOf(byCost.values());
        PasswordHash.SECURE_RANDOM.nextBytes(rememberKey);
    }

    /**
     * @return whether {@code request} carries an {@code Authorization: Basic} header
     */
    public static boolean hasBasicAuthorization(HttpRequest request) {
        for (String value : request.getHeader(AUTHORIZATION.toString())) {
            if (value.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        return authenticate(request).isAuthenticated();
    }

    @Override
    public AuthenticationResult authenticate(HttpRequest request) {
        String header = basicAuthorizationHeader(request);
        if (header == null) {
            throw new AuthenticationException("no basic authorization header found");
        }
        String source = sourceOf(request);
        if (!allowInsecure && !Boolean.TRUE.equals(request.isSecure())) {
            int unlogged = failuresToLog();
            if (unlogged >= 0) {
                warn(new LogEntry()
                    .setMessageFormat("control plane request sent basic credentials without TLS from {} - refused, use https or rotate the password if this connection crossed an untrusted network ({} earlier failures not logged)")
                    .setArguments(escapeControlCharactersForLog(source), unlogged));
            }
            throw new AuthenticationException("basic authentication requires TLS");
        }
        String[] usernameAndPassword = decode(header);
        if (usernameAndPassword == null) {
            throw new AuthenticationException("basic authorization header is invalid format");
        }
        String username = usernameAndPassword[0];
        String password = usernameAndPassword[1];
        BasicAuthenticationCredentials.User user = credentials.user(username);
        String fingerprint = fingerprint(username, password);
        // a credential this handler already verified costs no hashing, so it is admitted even while its source is
        // blocked: otherwise one guesser behind a shared NAT address would lock out every user behind it
        boolean remembered = user != null && isRemembered(fingerprint, username);
        long retryAfterMillis = remembered ? 0 : throttle.retryAfterMillis(source);
        if (retryAfterMillis > 0) {
            int unlogged = failuresToLog();
            if (unlogged >= 0) {
                warn(new LogEntry()
                    .setMessageFormat("control plane basic authentication refused for {} - too many failed attempts ({} earlier failures not logged)")
                    .setArguments(escapeControlCharactersForLog(source), unlogged));
            }
            throw new AuthenticationException("too many failed basic authentication attempts, retry in " + TimeUnit.MILLISECONDS.toSeconds(retryAfterMillis + 999) + " seconds");
        }
        boolean verified = remembered || verify(user, password);
        if (!verified) {
            throttle.failed(source);
            int unlogged = failuresToLog();
            if (unlogged >= 0) {
                warn(new LogEntry()
                    .setMessageFormat("control plane basic authentication failed for user \"{}\" from {} ({} earlier failures not logged)")
                    .setArguments(escapeControlCharactersForLog(username), escapeControlCharactersForLog(source), unlogged));
            }
            throw new AuthenticationException("control plane request failed basic authentication");
        }
        if (!remembered) {
            // a remembered admit must not clear the record, or a guesser sharing the address would get fresh guesses
            throttle.succeeded(source);
        }
        remember(fingerprint, username);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(USERNAME_CLAIM, username);
        return AuthenticationResult.authenticated(username, PRINCIPAL_SOURCE, claims, user.scopes());
    }

    /**
     * The verified user name, from {@code result} when this handler produced it, otherwise from authenticating
     * {@code request} again; null when it does not authenticate.
     */
    @Override
    public String principalIdentity(HttpRequest request, AuthenticationResult result) {
        if (result != null && PRINCIPAL_SOURCE.equals(result.getPrincipalSource()) && result.getPrincipal() != null) {
            return "basic:" + result.getPrincipal();
        }
        try {
            AuthenticationResult authenticated = request != null ? authenticate(request) : null;
            return authenticated != null && authenticated.getPrincipal() != null ? "basic:" + authenticated.getPrincipal() : null;
        } catch (AuthenticationException notAuthenticated) {
            return null;
        }
    }

    /**
     * Checks {@code password} against one hash of each distinct cost configured, the user's own for its cost, so
     * every attempt takes the same time whether the user exists, and whichever user it names.
     */
    private boolean verify(BasicAuthenticationCredentials.User user, String password) {
        boolean matches = false;
        String ownCost = user != null ? user.passwordHash().costKey() : null;
        for (PasswordHash representative : costRepresentatives) {
            if (representative.costKey().equals(ownCost)) {
                matches = user.passwordHash().matches(password);
            } else {
                representative.matches(password);
            }
        }
        return user != null && matches;
    }

    int trackedSources() {
        return throttle.trackedSources();
    }

    private static String basicAuthorizationHeader(HttpRequest request) {
        for (String value : request.getHeader(AUTHORIZATION.toString())) {
            if (value.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
                return value.substring(BASIC_PREFIX.length()).trim();
            }
        }
        return null;
    }

    private static String[] decode(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            return new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    /**
     * The remote address without its port, so every connection from one host shares a throttle record.
     */
    static String sourceOf(HttpRequest request) {
        String remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.isEmpty()) {
            return UNKNOWN_SOURCE;
        }
        int colon = remoteAddress.lastIndexOf(':');
        if (colon > 0 && colon < remoteAddress.length() - 1 && remoteAddress.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            remoteAddress = remoteAddress.substring(0, colon);
        }
        return remoteAddress.startsWith("[") && remoteAddress.endsWith("]") ? remoteAddress.substring(1, remoteAddress.length() - 1) : remoteAddress;
    }

    private String fingerprint(String username, String password) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rememberKey, "HmacSHA256"));
            mac.update(username.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private boolean isRemembered(String fingerprint, String username) {
        synchronized (remembered) {
            return username.equals(remembered.get(fingerprint));
        }
    }

    private void remember(String fingerprint, String username) {
        synchronized (remembered) {
            remembered.put(fingerprint, username);
        }
    }

    /**
     * @return -1 when a failure was logged less than {@value #FAILURE_LOG_INTERVAL_SECONDS} seconds ago (this one is
     * counted instead), otherwise how many failures went unlogged since the last one that was logged
     */
    private int failuresToLog() {
        long now = clockMillis.getAsLong();
        long last = lastFailureLogMillis.get();
        if (last != Long.MIN_VALUE && now - last < TimeUnit.SECONDS.toMillis(FAILURE_LOG_INTERVAL_SECONDS) || !lastFailureLogMillis.compareAndSet(last, now)) {
            unloggedFailures.incrementAndGet();
            return -1;
        }
        return unloggedFailures.getAndSet(0);
    }

    private void warn(LogEntry logEntry) {
        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(logEntry.setType(LogEntry.LogMessageType.AUTHENTICATION_FAILED).setLogLevel(Level.WARN));
        }
    }
}
