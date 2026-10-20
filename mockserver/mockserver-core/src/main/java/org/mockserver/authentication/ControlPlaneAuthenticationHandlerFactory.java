package org.mockserver.authentication;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.mockserver.authentication.jwt.JWTAuthenticationHandler;
import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.authentication.oidc.OidcAuthenticationHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.configuration.ControlPlaneAuthenticationSettings;
import org.mockserver.configuration.ServerTlsSettings;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.socket.tls.PEMToFile;
import org.slf4j.event.Level;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Builds the control-plane {@link AuthenticationHandler} chain from a {@link Configuration}.
 *
 * <p>This exists so the handler chain has exactly ONE construction site that is a pure function of the
 * live configuration. Previously the chain was built once during server bootstrap and pushed into
 * {@link org.mockserver.mock.HttpState}; enabling control-plane authentication afterwards (via a system
 * property, a {@link Configuration} mutation, or {@code PUT /mockserver/configuration}) returned success
 * but left the handler {@code null}, and a {@code null} handler means "authenticated" — so the control
 * plane reported itself locked while remaining fully open. Deriving the handler from the configuration on
 * demand, keyed by {@link #signature(Configuration)}, makes every configuration route take effect.
 *
 * <p><strong>Fail closed.</strong> {@link #build} returns {@code null} only when no control-plane
 * authentication is required. If authentication IS required but the handler cannot be constructed (bad
 * JWKS source, unreadable CA chain, ...) it returns a handler that rejects every request rather than
 * {@code null}, so a misconfiguration can never degrade into an open control plane.
 */
public class ControlPlaneAuthenticationHandlerFactory {

    private ControlPlaneAuthenticationHandlerFactory() {
    }

    /**
     * @return {@code true} if any control-plane authentication mechanism is enabled
     */
    public static boolean authenticationRequired(Configuration configuration) {
        return ControlPlaneAuthenticationSettings.of(configuration).authenticationRequired();
    }

    /**
     * A stable string capturing every configuration value that feeds handler construction. When this
     * changes the cached handler must be discarded and rebuilt, which is how a runtime reconfiguration
     * (system property, {@code Configuration} setter, DTO, or {@code PUT /mockserver/configuration})
     * reaches the enforcement point. See {@link ControlPlaneAuthenticationSettings#signature()}.
     */
    public static String signature(Configuration configuration) {
        return ControlPlaneAuthenticationSettings.of(configuration).signature();
    }

    /**
     * Build the control-plane authentication handler for the supplied configuration.
     *
     * @return {@code null} when no control-plane authentication is required; otherwise a handler that
     * enforces every enabled mechanism (chained when more than one is enabled), or a deny-all handler if
     * construction failed
     */
    public static AuthenticationHandler build(Configuration configuration, MockServerLogger mockServerLogger) {
        return build(ControlPlaneAuthenticationSettings.of(configuration), configuration, mockServerLogger);
    }

    /**
     * Build the control-plane authentication handler from one settings snapshot, so every mechanism it
     * enables and every value it is configured with come from the same configuration update.
     *
     * @param configuration supplies MockServer's CA certificate, which control-plane mTLS trusts only when no
     *                      {@code controlPlaneTLSMutualAuthenticationCAChain} is set
     */
    public static AuthenticationHandler build(ControlPlaneAuthenticationSettings settings, Configuration configuration, MockServerLogger mockServerLogger) {
        if (!settings.authenticationRequired()) {
            return null;
        }
        try {
            List<AuthenticationHandler> handlers = new ArrayList<>();
            if (Boolean.TRUE.equals(settings.controlPlaneTLSMutualAuthenticationRequired())) {
                handlers.add(new MTLSAuthenticationHandler(
                    mockServerLogger,
                    controlPlaneClientCertificateTrust(settings.controlPlaneTLSMutualAuthenticationCAChain(), configuration, mockServerLogger)
                ));
            }
            if (Boolean.TRUE.equals(settings.controlPlaneJWTAuthenticationRequired())) {
                handlers.add(new JWTAuthenticationHandler(mockServerLogger, settings.controlPlaneJWTAuthenticationJWKSource())
                    .withExpectedAudience(settings.controlPlaneJWTAuthenticationExpectedAudience())
                    .withMatchingClaims(settings.controlPlaneJWTAuthenticationMatchingClaims())
                    .withRequiredClaims(settings.controlPlaneJWTAuthenticationRequiredClaims()));
            }
            if (Boolean.TRUE.equals(settings.controlPlaneOidcAuthenticationRequired())) {
                handlers.add(new OidcAuthenticationHandler(
                    mockServerLogger,
                    settings.controlPlaneOidcJwksUri(),
                    settings.controlPlaneOidcIssuer(),
                    settings.controlPlaneOidcAudience(),
                    settings.controlPlaneOidcScopeClaim(),
                    settings.controlPlaneOidcRequiredScopes()
                ));
            }
            if (handlers.size() == 1) {
                return handlers.get(0);
            }
            return new ChainedAuthenticationHandler(handlers.toArray(new AuthenticationHandler[0]));
        } catch (Throwable throwable) {
            // Authentication IS required but could not be constructed. Returning null here would map to
            // "authenticated" at the enforcement point and silently open the control plane, so deny
            // everything instead and make the misconfiguration loud.
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("control plane authentication is enabled but the authentication handler could not be created - denying all control plane requests until the configuration is corrected")
                        .setThrowable(throwable)
                );
            }
            return new DenyAllAuthenticationHandler();
        }
    }

    /**
     * The certificate authorities a control-plane client certificate must chain to. With a
     * {@code controlPlaneTLSMutualAuthenticationCAChain}, only the certificates in it: MockServer's CA is not
     * added. Without one, MockServer's own CA certificate (one configured with {@code certificateAuthorityCertificate}
     * or dynamically created). The CA bundled with MockServer, whose private key is published in the jar, is never
     * trusted: if it would be, construction fails, so every control-plane request is denied.
     */
    static X509Certificate[] controlPlaneClientCertificateTrust(String controlPlaneTLSMutualAuthenticationCAChain, Configuration configuration, MockServerLogger mockServerLogger) {
        if (isNotBlank(controlPlaneTLSMutualAuthenticationCAChain)) {
            X509Certificate[] trusted = NettySslContextFactory.controlPlaneTrustCertificates(controlPlaneTLSMutualAuthenticationCAChain);
            if (containsBundledCertificateAuthority(trusted)) {
                throw ControlPlaneMutualTlsConfigurationException.bundledCertificateAuthorityInChain();
            }
            return trusted;
        }
        X509Certificate[] mockServerCertificateAuthority = new NettySslContextFactory(configuration, mockServerLogger, true).trustCertificateChain(null);
        if (containsBundledCertificateAuthority(mockServerCertificateAuthority)) {
            throw new ControlPlaneMutualTlsConfigurationException();
        }
        return mockServerCertificateAuthority;
    }

    /**
     * Refuses a configuration that requires control-plane mTLS and would trust the CA bundled with MockServer: no
     * {@code controlPlaneTLSMutualAuthenticationCAChain} while MockServer's CA certificate is the bundled CA, or a
     * {@code controlPlaneTLSMutualAuthenticationCAChain} that contains the bundled CA. Every start path calls this
     * before binding a port.
     *
     * @throws ControlPlaneMutualTlsConfigurationException naming the fix
     */
    public static void requireUsableControlPlaneMutualTls(Configuration configuration) {
        ControlPlaneAuthenticationSettings controlPlane = ControlPlaneAuthenticationSettings.of(configuration);
        ServerTlsSettings tls = ServerTlsSettings.of(configuration);
        requireUsableControlPlaneMutualTls(
            controlPlane.controlPlaneTLSMutualAuthenticationRequired(),
            controlPlane.controlPlaneTLSMutualAuthenticationCAChain(),
            tls.dynamicallyCreateCertificateAuthorityCertificate(),
            tls.certificateAuthorityCertificate()
        );
    }

    /**
     * The same check for the configuration {@code update} would leave, so {@code PUT /mockserver/configuration} can
     * refuse it before applying any of it.
     *
     * @throws ControlPlaneMutualTlsConfigurationException naming the fix
     */
    public static void requireUsableControlPlaneMutualTls(ConfigurationDTO update, Configuration configuration) {
        ControlPlaneAuthenticationSettings controlPlane = ControlPlaneAuthenticationSettings.of(configuration);
        ServerTlsSettings tls = ServerTlsSettings.of(configuration);
        requireUsableControlPlaneMutualTls(
            update.getControlPlaneTLSMutualAuthenticationRequired() != null ? update.getControlPlaneTLSMutualAuthenticationRequired() : controlPlane.controlPlaneTLSMutualAuthenticationRequired(),
            update.getControlPlaneTLSMutualAuthenticationCAChain() != null ? update.getControlPlaneTLSMutualAuthenticationCAChain() : controlPlane.controlPlaneTLSMutualAuthenticationCAChain(),
            update.getDynamicallyCreateCertificateAuthorityCertificate() != null ? update.getDynamicallyCreateCertificateAuthorityCertificate() : tls.dynamicallyCreateCertificateAuthorityCertificate(),
            update.getCertificateAuthorityCertificate() != null ? update.getCertificateAuthorityCertificate() : tls.certificateAuthorityCertificate()
        );
    }

    private static void requireUsableControlPlaneMutualTls(Boolean mutualTlsRequired, String controlPlaneTLSMutualAuthenticationCAChain, Boolean dynamicallyCreateCertificateAuthorityCertificate, String certificateAuthorityCertificate) {
        if (!Boolean.TRUE.equals(mutualTlsRequired)) {
            return;
        }
        if (isNotBlank(controlPlaneTLSMutualAuthenticationCAChain)) {
            if (chainContainsBundledCertificateAuthority(controlPlaneTLSMutualAuthenticationCAChain)) {
                throw ControlPlaneMutualTlsConfigurationException.bundledCertificateAuthorityInChain();
            }
        } else if (!Boolean.TRUE.equals(dynamicallyCreateCertificateAuthorityCertificate) && isBundledCertificateAuthority(certificateAuthorityCertificate)) {
            throw new ControlPlaneMutualTlsConfigurationException();
        }
    }

    private static boolean chainContainsBundledCertificateAuthority(String controlPlaneTLSMutualAuthenticationCAChain) {
        try {
            return containsBundledCertificateAuthority(NettySslContextFactory.controlPlaneTrustCertificates(controlPlaneTLSMutualAuthenticationCAChain));
        } catch (RuntimeException unreadable) {
            // an unreadable chain fails handler construction, which denies every control-plane request
            return false;
        }
    }

    private static boolean isBundledCertificateAuthority(String certificateAuthorityCertificate) {
        if (ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE.equals(certificateAuthorityCertificate)) {
            return true;
        }
        try {
            return containsBundledCertificateAuthority(new X509Certificate[]{PEMToFile.x509FromPEMFile(certificateAuthorityCertificate)});
        } catch (RuntimeException unreadable) {
            // an unreadable CA certificate fails the server's own TLS set-up with its own message
            return false;
        }
    }

    /**
     * Compares key material, not certificates or key encodings: RSA by modulus and public exponent whatever the
     * algorithm identifier (rsaEncryption or RSASSA-PSS), EC by curve and point. So a certificate re-issued or
     * re-encoded for the bundled CA's key is caught too.
     */
    static boolean containsBundledCertificateAuthority(X509Certificate[] certificates) {
        PublicKey bundledPublicKey = bundledCertificateAuthority().getPublicKey();
        for (X509Certificate certificate : certificates) {
            if (certificate != null && sameKeyMaterial(bundledPublicKey, certificate.getPublicKey())) {
                return true;
            }
        }
        return false;
    }

    static boolean sameKeyMaterial(PublicKey first, PublicKey second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        try {
            SubjectPublicKeyInfo firstInfo = SubjectPublicKeyInfo.getInstance(first.getEncoded());
            SubjectPublicKeyInfo secondInfo = SubjectPublicKeyInfo.getInstance(second.getEncoded());
            if (isRsa(firstInfo) && isRsa(secondInfo)) {
                RSAPublicKey firstRsa = RSAPublicKey.getInstance(firstInfo.parsePublicKey());
                RSAPublicKey secondRsa = RSAPublicKey.getInstance(secondInfo.parsePublicKey());
                return firstRsa.getModulus().equals(secondRsa.getModulus()) && firstRsa.getPublicExponent().equals(secondRsa.getPublicExponent());
            }
            if (first instanceof ECPublicKey && second instanceof ECPublicKey) {
                ECPublicKey firstEc = (ECPublicKey) first;
                ECPublicKey secondEc = (ECPublicKey) second;
                return firstEc.getW().equals(secondEc.getW()) && sameCurve(firstEc.getParams(), secondEc.getParams());
            }
            return Arrays.equals(firstInfo.getPublicKeyData().getBytes(), secondInfo.getPublicKeyData().getBytes());
        } catch (Exception unparseable) {
            return false;
        }
    }

    private static boolean isRsa(SubjectPublicKeyInfo info) {
        ASN1ObjectIdentifier algorithm = info.getAlgorithm().getAlgorithm();
        return PKCSObjectIdentifiers.rsaEncryption.equals(algorithm) || PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algorithm);
    }

    private static boolean sameCurve(ECParameterSpec first, ECParameterSpec second) {
        return first.getCurve().equals(second.getCurve())
            && first.getGenerator().equals(second.getGenerator())
            && first.getOrder().equals(second.getOrder())
            && first.getCofactor() == second.getCofactor();
    }

    private static X509Certificate bundledCertificateAuthority() {
        return PEMToFile.x509FromPEMFile(ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
    }

    /**
     * Rejects every control-plane request. Used when authentication is required but the configured
     * mechanism could not be constructed, so the failure mode is closed rather than open.
     */
    public static class DenyAllAuthenticationHandler implements AuthenticationHandler {

        @Override
        public boolean controlPlaneRequestAuthenticated(org.mockserver.model.HttpRequest request) {
            throw new AuthenticationException("control plane authentication is enabled but incorrectly configured", true);
        }
    }
}
