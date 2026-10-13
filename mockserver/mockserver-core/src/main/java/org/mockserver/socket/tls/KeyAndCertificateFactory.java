package org.mockserver.socket.tls;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * @author jamesdbloom
 */
public interface KeyAndCertificateFactory {

    /**
     * default key pair generation and signing algorithm
     */
    AsymmetricKeyPairAlgorithm DEFAULT_KEY_GENERATION_AND_SIGNING_ALGORITHM = AsymmetricKeyPairAlgorithm.RSA2048_SHA256;
    /**
     * Number of years the generated Certificate Authority remains valid. The generated CA is the trust
     * anchor users pin into their trust stores, so it needs to outlive a typical test/CI lifetime rather
     * than expiring after a single year and silently breaking pinned-CA deployments. Ten years is long
     * enough to avoid surprise expiry while staying well below the X.509 ceiling that older clients
     * (e.g. Apple iOS 8) reject.
     * <p>
     * This governs the CA only. The short-lived <em>leaf</em> (server) certificate has its own, much
     * shorter validity — see {@link #LEAF_CERTIFICATE_VALIDITY_DAYS_DEFAULT}. It also governs the HTTP/3
     * legacy echo-mode self-signed certificate, which is simultaneously trust anchor and server cert and
     * so must keep the long CA-style life (it has no renewal loop behind it).
     */
    long CERTIFICATE_VALIDITY_YEARS = 10;
    /**
     * Default number of days the auto-generated <em>leaf</em> (server) certificate remains valid: 397.
     * <p>
     * The operative constraint is Apple's <strong>825-day</strong> maximum for TLS server certificates
     * (iOS 13 / macOS 10.15, support.apple.com/en-us/103769), which has no carve-out for user-added
     * roots. Apple's shorter <em>398-day</em> ATS limit does <em>not</em> apply here: it explicitly
     * exempts certificates issued from user-added or administrator-added roots, which is exactly
     * MockServer's dynamically generated CA. 397 days sits comfortably inside the 825-day cap, and
     * proactive renewal at {@link #RENEWAL_ELAPSED_FRACTION} of validity elapsed (~318 days here)
     * keeps a long-running server from ever serving an expired leaf. Override via
     * {@code mockserver.sslCertificateLeafValidityInDays} to restore the old long-lived behaviour.
     */
    long LEAF_CERTIFICATE_VALIDITY_DAYS_DEFAULT = 397;
    /**
     * Minimum honoured leaf validity, in days: 30.
     * <p>
     * A positive override below this floor is a genuinely broken configuration, not a shorter-lived
     * certificate. The leaf's not-before bound is back-dated 5 days ({@link #notBefore()}), so a validity
     * of 1..5 days would place {@code notAfter} at or before {@code now} — the leaf would be born already
     * expired and {@code checkValidity(now)} would throw, failing generation outright. A validity of ~6
     * days would place the fresh leaf already past its {@link #RENEWAL_ELAPSED_FRACTION 80%-elapsed}
     * renewal threshold, so it would be re-minted on every handshake — a non-progress loop. The 30-day
     * floor clears the 5-day back-date plus a comfortable renewal margin: at 30 days the effective forward
     * life is ~25 days and renewal fires ~19 days out. A positive value below the floor is clamped UP to it
     * with a WARN so the operator sees their value was not honoured; a non-positive value instead falls back
     * to {@link #LEAF_CERTIFICATE_VALIDITY_DAYS_DEFAULT} (the documented "unset/invalid" behaviour).
     */
    long LEAF_CERTIFICATE_VALIDITY_DAYS_MIN = 30;
    /**
     * Maximum honoured leaf validity, in days: {@code CERTIFICATE_VALIDITY_YEARS * 365} (3650, ten years).
     * <p>
     * An unbounded override could push {@code notAfter} past the X.509 year-9999 ceiling
     * (9999-12-31, {@code new Date(253402300799000L)}) — or, well before that, past the point older Apple
     * clients accept (iOS 8, ~24 Jan 6084) — producing an unusable certificate. A leaf can never
     * usefully outlive the CA that signs it ({@link #CERTIFICATE_VALIDITY_YEARS} years) anyway, so this cap
     * both restores the historical long-lived (10-year) leaf exactly and keeps {@code notAfter} safely in
     * range. A value above the cap is clamped DOWN to it with a WARN.
     */
    long LEAF_CERTIFICATE_VALIDITY_DAYS_MAX = CERTIFICATE_VALIDITY_YEARS * 365;
    /**
     * Fraction of a certificate's validity window that must elapse before it is proactively renewed.
     * Renewing at 80% elapsed keeps a comfortable safety margin for both long- and short-lived
     * certificates, so a long-running server never keeps serving an expired leaf from its cached SSL
     * context.
     */
    double RENEWAL_ELAPSED_FRACTION = 0.8;

    /**
     * @param certificate the certificate to test (may be null)
     * @param fraction    the proportion of the validity window that must elapse before renewal
     * @param now         the current time in epoch milliseconds
     * @return true when {@code now} is at or past {@code notBefore + fraction * (notAfter - notBefore)};
     * false when {@code certificate} is null (nothing to renew yet)
     */
    static boolean isPastRenewalThreshold(X509Certificate certificate, double fraction, long now) {
        if (certificate == null) {
            return false;
        }
        long notBefore = certificate.getNotBefore().getTime();
        long notAfter = certificate.getNotAfter().getTime();
        long renewAt = notBefore + (long) ((notAfter - notBefore) * fraction);
        return now >= renewAt;
    }

    /**
     * @return true when the in-memory self-generated leaf (or the dynamically generated CA) has passed
     * its renewal threshold and must be regenerated before the cached TLS context is reused. Always
     * false for user-supplied fixed certificates — those are validated (and loudly rejected on expiry)
     * by {@link CertificateConfigurationValidator}. Defaults to false for factories that do not
     * self-renew (so third-party/mock implementations keep working unchanged).
     * <p>
     * Called on the Netty event loop for every TLS handshake: it must be cheap and must not block.
     */
    default boolean certificateNeedsRenewal() {
        return false;
    }

    /**
     * The not-before validity bound for a freshly issued certificate: the current time minus 5 days,
     * just in case the software clock goes back due to time synchronization.
     * <p>
     * Computed per issuance (rather than once at class load) so that certificates generated on the fly
     * — e.g. leaf certificates minted long after the JVM started — are anchored to issuance time rather
     * than to JVM start time.
     */
    static Date notBefore() {
        return new Date(System.currentTimeMillis() - 86400000L * 5);
    }

    /**
     * The not-after validity bound for a freshly issued <em>Certificate Authority</em> (or the HTTP/3
     * echo-mode self-signed anchor), {@link #CERTIFICATE_VALIDITY_YEARS} years in the future from
     * issuance time. Anchored to {@code now} (not to {@link #notBefore()}) so the full documented CA life
     * is available from issuance regardless of the notBefore back-dating.
     * <p>
     * The maximum possible value in the X.509 specification is 9999-12-31 23:59:59
     * (new Date(253402300799000L)), but Apple iOS 8 fails with a certificate
     * expiration date greater than Mon, 24 Jan 6084 02:07:59 GMT.
     * <p>
     * Computed per issuance (rather than once at class load) so that on-the-fly generated certificates
     * are anchored to issuance time rather than to JVM start time.
     */
    static Date notAfter() {
        return new Date(System.currentTimeMillis() + 86400000L * 365 * CERTIFICATE_VALIDITY_YEARS);
    }

    /**
     * The not-after validity bound for a freshly issued <em>leaf</em> (server) certificate, positioned so
     * the total validity window ({@code notAfter - notBefore}) is exactly {@code validityDays} days.
     * <p>
     * Anchored to the supplied {@code notBefore} (the 5-day back-dated bound) so the whole certificate
     * lifetime — the span Apple's 825-day cap actually measures — is bounded by {@code validityDays}.
     * With the {@link #LEAF_CERTIFICATE_VALIDITY_DAYS_DEFAULT default of 397}, the effective forward
     * validity from issuance is ~392 days, still far inside the 825-day cap.
     *
     * @param notBefore    the leaf's not-before bound (from {@link #notBefore()})
     * @param validityDays the total validity window in days
     */
    static Date notAfter(Date notBefore, long validityDays) {
        return new Date(notBefore.getTime() + 86400000L * validityDays);
    }

    /**
     * A positive, unpredictable certificate serial number. RFC 5280 §4.1.2.2 requires the serial to be a
     * positive integer, but {@code new BigInteger(64, SecureRandom)} draws from {@code [0, 2^64)} and so
     * can (rarely) yield zero, which strict validators reject. Falls back to {@code 1} in that single
     * case, preserving full entropy for every other draw.
     */
    static BigInteger positiveSerialNumber() {
        BigInteger serial = new BigInteger(64, new SecureRandom());
        return serial.signum() > 0 ? serial : BigInteger.ONE;
    }
    /**
     * CN for CA distinguishing name
     */
    String ROOT_COMMON_NAME = "www.mockserver.com";
    /**
     * default CN for leaf distinguishing name
     */
    String CERTIFICATE_DOMAIN = "localhost";
    /**
     * O for distinguishing name
     */
    String ORGANISATION = "MockServer";
    /**
     * L for distinguishing name
     */
    String LOCALITY = "London";
    /**
     * ST for distinguishing name
     */
    String STATE = "England";
    /**
     * C for distinguishing name
     */
    String COUNTRY = "UK";

    @SuppressWarnings("unused")
    void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();

    void buildAndSavePrivateKeyAndX509Certificate();

    /**
     * @return true when this factory generates certificates and has not generated the leaf yet.
     * Called on the Netty event loop for every TLS handshake: it must be cheap and must not block.
     */
    boolean certificateNotYetCreated();

    PrivateKey privateKey();

    X509Certificate x509Certificate();

    X509Certificate certificateAuthorityX509Certificate();

    List<X509Certificate> certificateChain();

    /**
     * Stable filename, under {@code directoryToSaveDynamicSSLCertificate}, that the active Certificate
     * Authority X.509 certificate (public certificate only — never the private key) is written to so it
     * can be pinned into client trust stores when MockServer is used as a TLS-intercepting proxy.
     */
    String PROXY_SETUP_CA_CERTIFICATE_FILE_NAME = "mockserver-ca.pem";

    /**
     * Materialise the active Certificate Authority X.509 certificate (the baked-in public CA, a custom
     * supplied CA, or the dynamically generated CA, whichever is in effect) to
     * {@code <directoryToSaveDynamicSSLCertificate>/}{@value #PROXY_SETUP_CA_CERTIFICATE_FILE_NAME}.
     * Only the public certificate is written, never the private key.
     * <p>
     * This default implementation is self-contained (no BouncyCastle dependency): it PEM-encodes the
     * DER bytes of {@link #certificateAuthorityX509Certificate()} and writes them atomically, using the
     * directory from the global {@link ConfigurationProperties#directoryToSaveDynamicSSLCertificate()}.
     * Implementations that hold their own {@code Configuration} (e.g. the BouncyCastle factory) override
     * this to honour their instance-scoped directory.
     *
     * @return the absolute path of the written CA certificate PEM file
     */
    default String writeCertificateAuthorityToDisk() {
        return writeCertificateAuthorityPem(certificateAuthorityX509Certificate(), ConfigurationProperties.directoryToSaveDynamicSSLCertificate());
    }

    /**
     * PEM-encode the public certificate (never a private key) and write it atomically to
     * {@code <directory>/}{@value #PROXY_SETUP_CA_CERTIFICATE_FILE_NAME} — written to a sibling temp file
     * then moved into place (ATOMIC_MOVE where supported) so a concurrent reader never observes a
     * truncated/empty file.
     *
     * @param caCertificate the public CA certificate to write
     * @param directory     the directory to write the {@value #PROXY_SETUP_CA_CERTIFICATE_FILE_NAME} file into
     * @return the absolute path of the written CA certificate PEM file
     */
    static String writeCertificateAuthorityPem(X509Certificate caCertificate, String directory) {
        File targetFile = new File(new File(directory), PROXY_SETUP_CA_CERTIFICATE_FILE_NAME);
        String absolutePath = targetFile.toPath().toAbsolutePath().normalize().toString();
        try {
            String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(caCertificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
            File parent = targetFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Path parentPath = parent != null ? parent.toPath() : targetFile.toPath().toAbsolutePath().getParent();
            Path tempPath = Files.createTempFile(parentPath, "mockserver-ca", ".pem.tmp");
            Files.write(tempPath, pem.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tempPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException atomicMoveNotSupported) {
                Files.move(tempPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return absolutePath;
        } catch (Exception exception) {
            throw new RuntimeException("exception while writing certificate authority X509 certificate to " + absolutePath, exception);
        }
    }

}
