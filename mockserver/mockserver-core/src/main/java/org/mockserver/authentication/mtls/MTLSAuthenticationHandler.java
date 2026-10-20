package org.mockserver.authentication.mtls;

import com.google.common.collect.ImmutableMap;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authenticates a control-plane request by its TLS client certificate.
 * <p>
 * Only the end-entity certificate (the first certificate of the presented chain, whose private key the TLS
 * handshake proved) is authenticated. A PKIX path is built from it to the configured certificate authorities,
 * using the other presented certificates only as untrusted intermediates. The end-entity certificate is
 * rejected when it is a certificate authority, is self-signed, or is itself one of the configured trust
 * anchors, when it is expired or not yet valid, when its Key Usage (if present) does not permit digitalSignature,
 * or when its Extended Key Usage (if present) does not permit clientAuth.
 * <p>
 * The {@link AuthenticationResult} carries the verified identity: the principal is the end-entity subject
 * distinguished name (or {@code sha256:<fingerprint>} when the subject is empty), the source is
 * {@value #PRINCIPAL_SOURCE}, and the claims hold the subject, issuer, serial number and
 * {@value #SHA256_FINGERPRINT_CLAIM} of that certificate. The fingerprint is the stable caller identity:
 * two certificates can share a subject.
 */
public class MTLSAuthenticationHandler implements AuthenticationHandler {

    public static final String PRINCIPAL_SOURCE = "verified-mtls";
    public static final String SUBJECT_DISTINGUISHED_NAME_CLAIM = "subjectDistinguishedName";
    public static final String ISSUER_DISTINGUISHED_NAME_CLAIM = "issuerDistinguishedName";
    public static final String SERIAL_NUMBER_CLAIM = "serialNumber";
    public static final String SHA256_FINGERPRINT_CLAIM = "sha256Fingerprint";

    // Extended Key Usage OIDs (RFC 5280 §4.2.1.12).
    private static final String ID_KP_CLIENT_AUTH = "1.3.6.1.5.5.7.3.2"; // TLS Web Client Authentication
    private static final String ANY_EXTENDED_KEY_USAGE = "2.5.29.37.0";  // anyExtendedKeyUsage
    private static final int MAXIMUM_INTERMEDIATES = 10;

    private final MockServerLogger mockServerLogger;
    private final X509Certificate[] controlPlaneTLSMutualAuthenticationCAChain;
    private final Set<TrustAnchor> trustAnchors;

    public MTLSAuthenticationHandler(MockServerLogger mockServerLogger, X509Certificate[] controlPlaneTLSMutualAuthenticationCAChain) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
        Set<TrustAnchor> anchors = new LinkedHashSet<>();
        if (controlPlaneTLSMutualAuthenticationCAChain != null) {
            for (X509Certificate caCertificate : controlPlaneTLSMutualAuthenticationCAChain) {
                if (caCertificate != null) {
                    anchors.add(new TrustAnchor(caCertificate, null));
                }
            }
        }
        this.trustAnchors = Collections.unmodifiableSet(anchors);
    }

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        return authenticate(request).isAuthenticated();
    }

    @Override
    public AuthenticationResult authenticate(HttpRequest request) {
        if (trustAnchors.isEmpty()) {
            throw new AuthenticationException("control plane request failed authentication no control plane CA specified");
        }
        List<org.mockserver.model.X509Certificate> presentedChain = request.getClientCertificateChain();
        if (presentedChain == null || presentedChain.isEmpty()) {
            throw new AuthenticationException("control plane request failed authentication no client certificates found");
        }
        org.mockserver.model.X509Certificate presentedLeaf = presentedChain.get(0);
        try {
            X509Certificate leaf = asX509(presentedLeaf == null ? null : presentedLeaf.getCertificate());
            List<X509Certificate> intermediates = new ArrayList<>();
            for (org.mockserver.model.X509Certificate presented : presentedChain.subList(1, Math.min(presentedChain.size(), MAXIMUM_INTERMEDIATES + 1))) {
                if (presented != null && presented.getCertificate() instanceof X509Certificate) {
                    intermediates.add((X509Certificate) presented.getCertificate());
                }
            }
            validateClientCertificate(leaf, intermediates);
            AuthenticationResult result = authenticatedResult(leaf);
            if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setHttpRequest(request)
                        .setMessageFormat("control plane request passed authentication with validated client certificate:{}")
                        .setArguments(describe(leaf))
                );
            }
            return result;
        } catch (Throwable throwable) {
            if (mockServerLogger.isEnabledForInstance(Level.TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.TRACE)
                        .setHttpRequest(request)
                        .setMessageFormat("exception validating client certificate:{}against control plane trust store certificates:{}")
                        .setArguments(
                            presentedLeaf == null ? "" : describe(presentedLeaf.getSerialNumber(), presentedLeaf.getIssuerDistinguishedName(), presentedLeaf.getSubjectDistinguishedName()),
                            Arrays.toString(Arrays.stream(controlPlaneTLSMutualAuthenticationCAChain).map(MTLSAuthenticationHandler::subjectOf).toArray())
                        )
                        .setThrowable(throwable)
                );
            }
            throw new AuthenticationException("control plane request failed authentication no client certificates can be validated by control plane CA");
        }
    }

    /**
     * The SHA-256 fingerprint of the verified client certificate, from {@code result} when this handler
     * produced it, otherwise from authenticating {@code request} again; null when it does not authenticate.
     */
    @Override
    public String principalIdentity(HttpRequest request, AuthenticationResult result) {
        AuthenticationResult verified = result != null && PRINCIPAL_SOURCE.equals(result.getPrincipalSource()) && result.getClaims().containsKey(SHA256_FINGERPRINT_CLAIM)
            ? result
            : authenticateQuietly(request);
        Object fingerprint = verified != null ? verified.getClaims().get(SHA256_FINGERPRINT_CLAIM) : null;
        return fingerprint != null ? "mtls:sha256:" + fingerprint : null;
    }

    private AuthenticationResult authenticateQuietly(HttpRequest request) {
        try {
            return request != null ? authenticate(request) : null;
        } catch (AuthenticationException notAuthenticated) {
            return null;
        }
    }

    private void validateClientCertificate(X509Certificate leaf, List<X509Certificate> intermediates) throws GeneralSecurityException {
        if (leaf.getBasicConstraints() != -1) {
            throw new CertPathValidatorException("client certificate is a certificate authority (basicConstraints CA=true)");
        }
        if (isSelfSigned(leaf)) {
            throw new CertPathValidatorException("client certificate is self-signed");
        }
        for (TrustAnchor trustAnchor : trustAnchors) {
            if (leaf.equals(trustAnchor.getTrustedCert())) {
                throw new CertPathValidatorException("client certificate is a configured trust anchor");
            }
        }
        boolean[] keyUsage = leaf.getKeyUsage();
        if (keyUsage != null && (keyUsage.length == 0 || !keyUsage[0])) {
            throw new CertPathValidatorException("client certificate key usage does not permit digitalSignature");
        }
        if (!hasClientAuthExtendedKeyUsage(leaf)) {
            throw new CertPathValidatorException("client certificate extended key usage does not permit clientAuth (id-kp-clientAuth 1.3.6.1.5.5.7.3.2)");
        }
        X509CertSelector target = new X509CertSelector();
        target.setCertificate(leaf);
        PKIXBuilderParameters parameters = new PKIXBuilderParameters(trustAnchors, target);
        parameters.setRevocationEnabled(false);
        List<X509Certificate> candidates = new ArrayList<>(intermediates.size() + 1);
        candidates.add(leaf);
        candidates.addAll(intermediates);
        parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(candidates)));
        PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
        List<? extends Certificate> path = result.getCertPath().getCertificates();
        if (path.isEmpty() || !leaf.equals(path.get(0))) {
            throw new CertPathValidatorException("validated certification path does not start with the client certificate");
        }
    }

    private static X509Certificate asX509(Certificate certificate) throws CertPathValidatorException {
        if (!(certificate instanceof X509Certificate)) {
            throw new CertPathValidatorException("presented certificate is not an X.509 certificate");
        }
        return (X509Certificate) certificate;
    }

    static boolean isSelfSigned(X509Certificate certificate) {
        if (!certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (Exception notSelfSigned) {
            return false;
        }
    }

    /**
     * Returns {@code true} when the certificate either carries no Extended Key Usage extension (an
     * unrestricted certificate, allowed per RFC 5280 practice) or its EKU permits clientAuth
     * ({@code id-kp-clientAuth}) or anyExtendedKeyUsage. Returns {@code false} only when an EKU
     * extension is present but does not include clientAuth.
     */
    static boolean hasClientAuthExtendedKeyUsage(X509Certificate certificate) throws CertificateParsingException {
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        if (extendedKeyUsage == null) {
            return true;
        }
        return extendedKeyUsage.contains(ID_KP_CLIENT_AUTH) || extendedKeyUsage.contains(ANY_EXTENDED_KEY_USAGE);
    }

    private static AuthenticationResult authenticatedResult(X509Certificate leaf) throws GeneralSecurityException {
        String subject = leaf.getSubjectX500Principal().getName();
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(SUBJECT_DISTINGUISHED_NAME_CLAIM, subject);
        claims.put(ISSUER_DISTINGUISHED_NAME_CLAIM, leaf.getIssuerX500Principal().getName());
        claims.put(SERIAL_NUMBER_CLAIM, leaf.getSerialNumber().toString());
        claims.put(SHA256_FINGERPRINT_CLAIM, fingerprint);
        return AuthenticationResult.authenticated(
            subject.isEmpty() ? "sha256:" + fingerprint : subject,
            PRINCIPAL_SOURCE,
            claims,
            Set.of()
        );
    }

    private static String subjectOf(X509Certificate certificate) {
        return certificate == null ? "" : certificate.getSubjectX500Principal().getName();
    }

    private String describe(X509Certificate certificate) {
        return describe(certificate.getSerialNumber().toString(), certificate.getIssuerX500Principal().getName(), certificate.getSubjectX500Principal().getName());
    }

    private String describe(String serialNumber, String issuerDistinguishedName, String subjectDistinguishedName) {
        try {
            return ObjectMapperFactory.createObjectMapper(true, false).writeValueAsString(ImmutableMap.of(
                "serialNumber", String.valueOf(serialNumber),
                "issuerDistinguishedName", String.valueOf(issuerDistinguishedName),
                "subjectDistinguishedName", String.valueOf(subjectDistinguishedName)
            ));
        } catch (Throwable throwable) {
            if (mockServerLogger.isEnabledForInstance(Level.TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.TRACE)
                        .setMessageFormat("exception serialising certificate information")
                        .setThrowable(throwable)
                );
            }
            return "";
        }
    }

}
