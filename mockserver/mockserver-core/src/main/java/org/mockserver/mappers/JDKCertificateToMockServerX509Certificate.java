package org.mockserver.mappers;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.X509Certificate;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.slf4j.event.Level.INFO;

public class JDKCertificateToMockServerX509Certificate {

    private final MockServerLogger mockServerLogger;

    public JDKCertificateToMockServerX509Certificate(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public HttpRequest setClientCertificates(HttpRequest httpRequest, Certificate[] clientCertificates) {
        if (clientCertificates != null) {
            List<X509Certificate> x509Certificates = Arrays
                .stream(clientCertificates)
                .flatMap(certificate -> {
                             try {
                                 java.security.cert.X509Certificate x509Certificate = (java.security.cert.X509Certificate) CertificateFactory
                                     .getInstance("X.509")
                                     .generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
                                 return Stream.of(
                                     new X509Certificate()
                                         .withSerialNumber(x509Certificate.getSerialNumber().toString())
                                         .withIssuerDistinguishedName(x509Certificate.getIssuerX500Principal().getName())
                                         .withSubjectDistinguishedName(x509Certificate.getSubjectX500Principal().getName())
                                         .withSignatureAlgorithmName(x509Certificate.getSigAlgName())
                                         .withCertificate(certificate)
                                 );
                             } catch (Throwable throwable) {
                                 if (MockServerLogger.isEnabled(INFO) && mockServerLogger != null) {
                                     mockServerLogger.logEvent(
                                         new LogEntry()
                                             .setLogLevel(INFO)
                                             .setHttpRequest(httpRequest)
                                             .setMessageFormat("exception decoding client certificate " + throwable.getMessage())
                                             .setThrowable(throwable)
                                     );
                                 }
                             }
                             return Stream.empty();
                         }
                )
                .collect(Collectors.toList());
            if (!x509Certificates.isEmpty()) {
                httpRequest.withClientCertificateChain(x509Certificates);
            }
        }
        return httpRequest;
    }

}
