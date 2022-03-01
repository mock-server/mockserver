package org.mockserver.authentication.mtls;

import com.google.common.collect.ImmutableMap;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.ControlPlaneAuthenticationHandler;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.security.cert.X509Certificate;

public class ControlPlaneMTLSAuthenticationHandler implements ControlPlaneAuthenticationHandler {

    private final MockServerLogger mockServerLogger;
    private final X509Certificate[] controlPlaneTLSMutualAuthenticationCAChain;

    public ControlPlaneMTLSAuthenticationHandler(MockServerLogger mockServerLogger, X509Certificate[] controlPlaneTLSMutualAuthenticationCAChain) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
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
                throw new AuthenticationException("control plane request failed authentication no client certificates can be validated by control plane CA");
            } else {
                throw new AuthenticationException("control plane request failed authentication no client certificates found");
            }
        }
        throw new AuthenticationException("control plane request failed authentication no control plane CA specified");
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
