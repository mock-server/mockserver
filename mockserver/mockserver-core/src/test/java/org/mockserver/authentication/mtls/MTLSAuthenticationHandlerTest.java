package org.mockserver.authentication.mtls;

import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.JDKCertificateToMockServerX509Certificate;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.tls.PEMToFile;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

public class MTLSAuthenticationHandlerTest {

    private static final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldValidateCertificate() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateCertificateWithMultipleCAsMatchingFirst() {
        // given
        List<X509Certificate> controlPlaneTLSMutualAuthenticationCAChain = new ArrayList<>();
        controlPlaneTLSMutualAuthenticationCAChain.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem"));
        controlPlaneTLSMutualAuthenticationCAChain.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem"));

        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            controlPlaneTLSMutualAuthenticationCAChain.toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateCertificateWithMultipleCAsMatchingSecond() {
        // given
        List<X509Certificate> controlPlaneTLSMutualAuthenticationCAChain = new ArrayList<>();
        controlPlaneTLSMutualAuthenticationCAChain.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem"));
        controlPlaneTLSMutualAuthenticationCAChain.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem"));

        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            controlPlaneTLSMutualAuthenticationCAChain.toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateCertificateWithPeerCertificatesMatchingFirst() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );

        List<X509Certificate> clientCertificates = new ArrayList<>();
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/leaf-cert.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem"));
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            clientCertificates.toArray(new X509Certificate[0])
        );

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateCertificateWithPeerCertificatesMatchingSecond() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );

        List<X509Certificate> clientCertificates = new ArrayList<>();
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/leaf-cert.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem"));
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            clientCertificates.toArray(new X509Certificate[0])
        );

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldNotValidateCertificate() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates can be validated by control plane CA"));
    }

    @Test
    public void shouldNotValidateCertificateChain() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        List<X509Certificate> clientCertificates = new ArrayList<>();
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem"));
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            clientCertificates.toArray(new X509Certificate[0])
        );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates can be validated by control plane CA"));
    }

    @Test
    public void shouldNotValidateEmptyClientCertificates() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            new X509Certificate[0]
        );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates found"));
    }

    @Test
    public void shouldNotValidateNoClientCertificates() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = request();

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates found"));
    }

    @Test
    public void shouldNotValidateEmptyCACertificates() {
        // given
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            new X509Certificate[0]
        );
        List<X509Certificate> clientCertificates = new ArrayList<>();
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem"));
        clientCertificates.addAll(PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem"));
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            clientCertificates.toArray(new X509Certificate[0])
        );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no control plane CA specified"));
    }

}