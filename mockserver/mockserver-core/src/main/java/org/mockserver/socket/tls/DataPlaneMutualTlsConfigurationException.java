package org.mockserver.socket.tls;

/**
 * Data-plane mTLS ({@code tlsMutualAuthenticationRequired}, or a {@code tlsMutualAuthenticationCertificateChain}) would
 * trust client certificates signed by the CA bundled with MockServer, whose private key is published in the jar. MockServer
 * refuses to start, {@code PUT /mockserver/configuration} answers {@code 400}, and the TLS context is not rebuilt, with
 * the message, which names the fix.
 */
public class DataPlaneMutualTlsConfigurationException extends IllegalArgumentException {

    static final String MESSAGE = "tlsMutualAuthenticationRequired is enabled while MockServer's CA certificate is the default CA bundled with MockServer,"
        + " whose private key is published in the MockServer jar, so anyone could create a client certificate MockServer accepts:"
        + " configure your own CA with certificateAuthorityCertificate and certificateAuthorityPrivateKey,"
        + " or set dynamicallyCreateCertificateAuthorityCertificate=true,"
        + " or set tlsMutualAuthenticationCertificateChain to the CA chain that signs your client certificates;"
        + " for a throwaway local test only, tlsMutualAuthenticationAllowBundledCa=true restores the old behaviour";

    static final String BUNDLED_CA_IN_CHAIN_MESSAGE = "tlsMutualAuthenticationCertificateChain contains the default CA bundled with MockServer,"
        + " whose private key is published in the MockServer jar, so anyone could create a client certificate MockServer accepts:"
        + " remove it from tlsMutualAuthenticationCertificateChain and use the CA chain that signs your client certificates,"
        + " configure your own CA with certificateAuthorityCertificate and certificateAuthorityPrivateKey,"
        + " or set dynamicallyCreateCertificateAuthorityCertificate=true;"
        + " for a throwaway local test only, tlsMutualAuthenticationAllowBundledCa=true restores the old behaviour";

    private DataPlaneMutualTlsConfigurationException(String message) {
        super(message);
    }

    static DataPlaneMutualTlsConfigurationException bundledCertificateAuthorityIsMockServerCa() {
        return new DataPlaneMutualTlsConfigurationException(MESSAGE);
    }

    static DataPlaneMutualTlsConfigurationException bundledCertificateAuthorityInChain() {
        return new DataPlaneMutualTlsConfigurationException(BUNDLED_CA_IN_CHAIN_MESSAGE);
    }
}
