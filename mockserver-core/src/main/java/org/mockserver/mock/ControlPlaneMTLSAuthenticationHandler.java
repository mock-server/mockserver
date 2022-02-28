package org.mockserver.mock;

import com.google.common.collect.ImmutableMap;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import java.security.cert.X509Certificate;

public class ControlPlaneMTLSAuthenticationHandler implements ControlPlaneAuthenticationHandler {

    private final MockServerLogger mockServerLogger;
    private X509Certificate[] controlPlaneTLSMutualAuthenticationCAChain;

    public ControlPlaneMTLSAuthenticationHandler(MockServerLogger mockServerLogger, NettySslContextFactory nettySslContextFactory) {
        this.mockServerLogger = mockServerLogger;
        if (ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired()) {
            controlPlaneTLSMutualAuthenticationCAChain = nettySslContextFactory.trustCertificateChain(ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain());
        }
    }

    @Override
    public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
        if (controlPlaneTLSMutualAuthenticationCAChain != null && controlPlaneTLSMutualAuthenticationCAChain.length != 0) {
            if (request.getClientCertificateChain() != null) {
                for (org.mockserver.model.X509Certificate clientCertificate : request.getClientCertificateChain()) {
                    for (X509Certificate caCertificate : controlPlaneTLSMutualAuthenticationCAChain) {
                        String clientCertificateInformation = getClientCertificateInformation(
                            clientCertificate.getSerialNumber(),
                            clientCertificate.getIssuerDistinguishedName(),
                            clientCertificate.getSubjectDistinguishedName()
                        );
                        String caCertificateInformation = getClientCertificateInformation(
                            caCertificate.getSerialNumber().toString(),
                            caCertificate.getIssuerDN().getName(),
                            caCertificate.getSubjectDN().getName()
                        );
                        try {
                            clientCertificate.getCertificate().verify(caCertificate.getPublicKey());
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.DEBUG)
                                    .setHttpRequest(request)
                                    .setMessageFormat("validated client certificate:{}against control plane trust store certificate:{}")
                                    .setArguments(clientCertificateInformation, caCertificateInformation)
                            );
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.DEBUG)
                                    .setHttpRequest(request)
                                    .setMessageFormat("control plane request passed authentication:{}")
                                    .setArguments(request)
                            );
                            return true;
                        } catch (Throwable throwable) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.TRACE)
                                    .setHttpRequest(request)
                                    .setMessageFormat("exception validating client certificate:{}against control plane trust store certificate:{}")
                                    .setArguments(clientCertificateInformation, caCertificateInformation)
                                    .setThrowable(throwable)
                            );
                        }
                    }
                }
            }
        }
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(request)
                .setMessageFormat("control plane request failed authentication:{}")
                .setArguments(request)
        );
        return false;
    }

    private String getClientCertificateInformation(String serialNumber, String issuerDistinguishedName, String subjectDistinguishedName) {
        try {
            return ObjectMapperFactory.createObjectMapper(true).writeValueAsString(ImmutableMap.of(
                "serialNumber", serialNumber,
                "issuerDistinguishedName", issuerDistinguishedName,
                "subjectDistinguishedName", subjectDistinguishedName
            ));
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.TRACE)
                    .setMessageFormat("exception serialising certificate information")
                    .setThrowable(throwable)
            );
            return "";
        }
    }

}
