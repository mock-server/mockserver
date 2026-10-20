package org.mockserver.authentication;

/**
 * Control-plane mTLS would trust the CA bundled with MockServer, whose private key is published in the jar: either
 * no control-plane CA chain is set while MockServer's CA certificate is the bundled CA, or the control-plane CA chain
 * contains the bundled CA. MockServer refuses to start, and {@code PUT /mockserver/configuration} answers
 * {@code 400}, with the message, which names the fix.
 */
public class ControlPlaneMutualTlsConfigurationException extends IllegalArgumentException {

    static final String MESSAGE = "controlPlaneTLSMutualAuthenticationRequired is enabled without controlPlaneTLSMutualAuthenticationCAChain"
        + " while MockServer's CA certificate is the default CA bundled with MockServer, which must not be trusted for control-plane"
        + " client certificates: set controlPlaneTLSMutualAuthenticationCAChain to the CA chain that signs your control-plane client"
        + " certificates, or configure MockServer's own CA with certificateAuthorityCertificate and certificateAuthorityPrivateKey";

    static final String BUNDLED_CA_IN_CHAIN_MESSAGE = "controlPlaneTLSMutualAuthenticationCAChain contains the default CA bundled with MockServer,"
        + " whose private key is published in the MockServer jar, so anyone could create a client certificate the control plane accepts:"
        + " remove it from controlPlaneTLSMutualAuthenticationCAChain and use the CA chain that signs your control-plane client certificates";

    public ControlPlaneMutualTlsConfigurationException() {
        super(MESSAGE);
    }

    private ControlPlaneMutualTlsConfigurationException(String message) {
        super(message);
    }

    static ControlPlaneMutualTlsConfigurationException bundledCertificateAuthorityInChain() {
        return new ControlPlaneMutualTlsConfigurationException(BUNDLED_CA_IN_CHAIN_MESSAGE);
    }
}
