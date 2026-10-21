package org.mockserver.socket.tls;

import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ServerTlsSettings;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.slf4j.event.Level;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.socket.tls.PEMToFile.x509ChainFromPEMFile;

/**
 * Which certificate authorities data-plane mTLS trusts for client certificates, on every TLS listener (TCP and HTTP/3).
 *
 * <p>Data-plane mTLS never trusts the CA bundled with MockServer, whose private key is published in the jar, unless
 * {@code tlsMutualAuthenticationAllowBundledCa} is enabled:
 * <ul>
 *     <li>with a {@code tlsMutualAuthenticationCertificateChain}: the certificates in it, plus MockServer's CA certificate
 *     unless that is the bundled CA; a chain containing the bundled CA, or any certificate for its key, is refused</li>
 *     <li>without one: MockServer's CA certificate, which is refused if it is the bundled CA</li>
 * </ul>
 */
public final class DataPlaneMutualTlsTrust {

    static final String BUNDLED_CA_TRUSTED_WARNING = "tlsMutualAuthenticationAllowBundledCa is enabled, so mTLS trusts client certificates signed by the default CA bundled with MockServer,"
        + " whose private key is published in the MockServer jar: ANYONE CAN CREATE A CLIENT CERTIFICATE MOCKSERVER ACCEPTS, with any subject, issuer or serial number,"
        + " so mTLS does not authenticate clients and client certificate matchers can be satisfied by anyone; use this only for throwaway local tests";

    private enum Outcome {
        /** the bundled CA is not trusted */
        NOT_TRUSTED,
        /** a chain is set, and MockServer's CA is left out because it is the bundled CA */
        CHAIN_LEAVES_OUT_MOCKSERVER_CA,
        /** refused unless the opt-in is set: no chain, and MockServer's CA is the bundled CA */
        MOCKSERVER_CA_IS_BUNDLED,
        /** refused unless the opt-in is set: the chain contains the bundled CA */
        CHAIN_CONTAINS_BUNDLED_CA
    }

    private DataPlaneMutualTlsTrust() {
    }

    /**
     * The certificates a data-plane client certificate must chain to. Every server TLS context build (TCP and HTTP/3)
     * calls this, so with {@code tlsMutualAuthenticationAllowBundledCa} it logs the WARN whenever a built context
     * trusts the bundled CA.
     *
     * @param tlsMutualAuthenticationCertificateChain the configured chain, blank for none
     * @param mockServerCertificateAuthority          MockServer's CA certificate
     * @param allowBundledCertificateAuthority        {@code tlsMutualAuthenticationAllowBundledCa}
     * @param mockServerLogger                        for the WARN, may be {@code null}
     * @throws DataPlaneMutualTlsConfigurationException naming the fix, if they would include the bundled CA
     */
    public static X509Certificate[] clientCertificateTrust(String tlsMutualAuthenticationCertificateChain, X509Certificate mockServerCertificateAuthority, boolean allowBundledCertificateAuthority, MockServerLogger mockServerLogger) {
        List<X509Certificate> trusted = isNotBlank(tlsMutualAuthenticationCertificateChain) ? x509ChainFromPEMFile(tlsMutualAuthenticationCertificateChain) : new ArrayList<>();
        if (allowBundledCertificateAuthority) {
            trusted.add(mockServerCertificateAuthority);
            X509Certificate[] trustedArray = trusted.toArray(new X509Certificate[0]);
            if (BundledCertificateAuthority.containsBundledCertificateAuthority(trustedArray)) {
                warn(mockServerLogger, new LogEntry().setMessageFormat(BUNDLED_CA_TRUSTED_WARNING));
            }
            return trustedArray;
        }
        if (BundledCertificateAuthority.containsBundledCertificateAuthority(trusted.toArray(new X509Certificate[0]))) {
            throw DataPlaneMutualTlsConfigurationException.bundledCertificateAuthorityInChain();
        }
        if (BundledCertificateAuthority.containsBundledCertificateAuthority(new X509Certificate[]{mockServerCertificateAuthority})) {
            if (isBlank(tlsMutualAuthenticationCertificateChain)) {
                throw DataPlaneMutualTlsConfigurationException.bundledCertificateAuthorityIsMockServerCa();
            }
        } else {
            trusted.add(mockServerCertificateAuthority);
        }
        return trusted.toArray(new X509Certificate[0]);
    }

    /**
     * Refuses a configuration whose data-plane mTLS would trust the bundled CA, or, when
     * {@code tlsMutualAuthenticationAllowBundledCa} is enabled, logs a WARN that it does. Every start path calls this
     * before binding a port.
     *
     * @throws DataPlaneMutualTlsConfigurationException naming the fix
     */
    public static void requireUsableDataPlaneMutualTls(Configuration configuration, MockServerLogger mockServerLogger) {
        ServerTlsSettings tls = ServerTlsSettings.of(configuration);
        Outcome outcome = outcome(
            tls.tlsMutualAuthenticationRequired(),
            tls.tlsMutualAuthenticationCertificateChain(),
            tls.dynamicallyCreateCertificateAuthorityCertificate(),
            tls.certificateAuthorityCertificate(),
            configuration,
            mockServerLogger
        );
        if (outcome == Outcome.NOT_TRUSTED) {
            return;
        }
        if (Boolean.TRUE.equals(configuration.tlsMutualAuthenticationAllowBundledCa())) {
            warn(mockServerLogger, new LogEntry().setMessageFormat(BUNDLED_CA_TRUSTED_WARNING));
            return;
        }
        throwIfRefused(outcome);
        warn(mockServerLogger, new LogEntry()
            .setMessageFormat("mTLS trusts only tlsMutualAuthenticationCertificateChain:{}because MockServer's CA certificate is the default CA bundled with MockServer, whose private key is published,"
                + " so client certificates signed by MockServer's CA, including the one MockServerClient presents by default, are not accepted;"
                + " to trust MockServer's CA too, configure your own CA with certificateAuthorityCertificate and certificateAuthorityPrivateKey or set dynamicallyCreateCertificateAuthorityCertificate=true")
            .setArguments(tls.tlsMutualAuthenticationCertificateChain()));
    }

    /**
     * The same check for the configuration {@code update} would leave, so {@code PUT /mockserver/configuration} can
     * refuse it before applying any of it, or, with {@code tlsMutualAuthenticationAllowBundledCa}, log the WARN when
     * the update makes mTLS trust the bundled CA. That property is fixed at startup, so it is read from
     * {@code configuration}, never from {@code update}.
     *
     * @throws DataPlaneMutualTlsConfigurationException naming the fix
     */
    public static void requireUsableDataPlaneMutualTls(ConfigurationDTO update, Configuration configuration, MockServerLogger mockServerLogger) {
        ServerTlsSettings tls = ServerTlsSettings.of(configuration);
        Outcome after = outcome(
            update.getTlsMutualAuthenticationRequired() != null ? update.getTlsMutualAuthenticationRequired() : tls.tlsMutualAuthenticationRequired(),
            update.getTlsMutualAuthenticationCertificateChain() != null ? update.getTlsMutualAuthenticationCertificateChain() : tls.tlsMutualAuthenticationCertificateChain(),
            update.getDynamicallyCreateCertificateAuthorityCertificate() != null ? update.getDynamicallyCreateCertificateAuthorityCertificate() : tls.dynamicallyCreateCertificateAuthorityCertificate(),
            update.getCertificateAuthorityCertificate() != null ? update.getCertificateAuthorityCertificate() : tls.certificateAuthorityCertificate(),
            configuration,
            mockServerLogger
        );
        if (!Boolean.TRUE.equals(configuration.tlsMutualAuthenticationAllowBundledCa())) {
            throwIfRefused(after);
            return;
        }
        if (after != Outcome.NOT_TRUSTED && outcome(
            tls.tlsMutualAuthenticationRequired(),
            tls.tlsMutualAuthenticationCertificateChain(),
            tls.dynamicallyCreateCertificateAuthorityCertificate(),
            tls.certificateAuthorityCertificate(),
            configuration,
            mockServerLogger
        ) == Outcome.NOT_TRUSTED) {
            warn(mockServerLogger, new LogEntry().setMessageFormat(BUNDLED_CA_TRUSTED_WARNING));
        }
    }

    private static Outcome outcome(Boolean tlsMutualAuthenticationRequired, String tlsMutualAuthenticationCertificateChain, Boolean dynamicallyCreateCertificateAuthorityCertificate, String certificateAuthorityCertificate, Configuration configuration, MockServerLogger mockServerLogger) {
        if (isNotBlank(tlsMutualAuthenticationCertificateChain)) {
            if (BundledCertificateAuthority.chainContainsBundledCertificateAuthority(tlsMutualAuthenticationCertificateChain)) {
                return Outcome.CHAIN_CONTAINS_BUNDLED_CA;
            }
            return mockServerCertificateAuthorityIsBundled(dynamicallyCreateCertificateAuthorityCertificate, certificateAuthorityCertificate, configuration, mockServerLogger)
                ? Outcome.CHAIN_LEAVES_OUT_MOCKSERVER_CA
                : Outcome.NOT_TRUSTED;
        }
        if (Boolean.TRUE.equals(tlsMutualAuthenticationRequired)
            && mockServerCertificateAuthorityIsBundled(dynamicallyCreateCertificateAuthorityCertificate, certificateAuthorityCertificate, configuration, mockServerLogger)) {
            return Outcome.MOCKSERVER_CA_IS_BUNDLED;
        }
        return Outcome.NOT_TRUSTED;
    }

    private static void throwIfRefused(Outcome outcome) {
        if (outcome == Outcome.MOCKSERVER_CA_IS_BUNDLED) {
            throw DataPlaneMutualTlsConfigurationException.bundledCertificateAuthorityIsMockServerCa();
        }
        if (outcome == Outcome.CHAIN_CONTAINS_BUNDLED_CA) {
            throw DataPlaneMutualTlsConfigurationException.bundledCertificateAuthorityInChain();
        }
    }

    /**
     * A custom {@link KeyAndCertificateFactorySupplier} decides the CA itself, so its CA certificate is checked rather
     * than the configured one; if it cannot supply one, the configured values decide. Only called when mTLS is on.
     */
    private static boolean mockServerCertificateAuthorityIsBundled(Boolean dynamicallyCreateCertificateAuthorityCertificate, String certificateAuthorityCertificate, Configuration configuration, MockServerLogger mockServerLogger) {
        if (KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier() != null) {
            try {
                X509Certificate certificateAuthority = KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(configuration, mockServerLogger).certificateAuthorityX509Certificate();
                if (certificateAuthority != null) {
                    return BundledCertificateAuthority.containsBundledCertificateAuthority(new X509Certificate[]{certificateAuthority});
                }
            } catch (RuntimeException unavailable) {
                // fall back to the configured values
            }
        }
        return !Boolean.TRUE.equals(dynamicallyCreateCertificateAuthorityCertificate)
            && BundledCertificateAuthority.isBundledCertificateAuthority(certificateAuthorityCertificate);
    }

    private static void warn(MockServerLogger mockServerLogger, LogEntry logEntry) {
        if (mockServerLogger != null) {
            mockServerLogger.logEvent(logEntry.setType(SERVER_CONFIGURATION).setLogLevel(Level.WARN));
        }
    }
}
