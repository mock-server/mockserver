package org.mockserver.authentication.mtls;

import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.JDKCertificateToMockServerX509Certificate;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TestCertificates;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;

public class MTLSAuthenticationHandlerTest {

    private static final MockServerLogger mockServerLogger = new MockServerLogger();
    private static final String NOT_VALIDATED = "control plane request failed authentication no client certificates can be validated by control plane CA";

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
    public void shouldNotValidateWhenOnlyALaterCertificateInThePresentedChainIsTrusted() {
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
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo(NOT_VALIDATED));
    }

    @Test
    public void shouldNotValidateExpiredCertificate() {
        // given - signed by the control plane CA but expired on 2023-01-01
        AuthenticationHandler authenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/expired-leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates can be validated by control plane CA"));
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

    // Extended Key Usage OIDs (RFC 5280 §4.2.1.12).
    private static final String CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2";
    private static final String SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1";
    private static final String ANY_EKU_OID = "2.5.29.37.0";

    @Test
    public void shouldAllowCertificateWithNoExtendedKeyUsage() throws Exception {
        // given - a certificate with no EKU extension is unrestricted and must be permitted (RFC 5280)
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getExtendedKeyUsage()).thenReturn(null);

        // then
        assertTrue(MTLSAuthenticationHandler.hasClientAuthExtendedKeyUsage(certificate));
    }

    @Test
    public void shouldAllowCertificateWithClientAuthExtendedKeyUsage() throws Exception {
        // given - an EKU that includes clientAuth (alongside serverAuth, as the repo's test certs do)
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getExtendedKeyUsage()).thenReturn(Arrays.asList(SERVER_AUTH_OID, CLIENT_AUTH_OID));

        // then
        assertTrue(MTLSAuthenticationHandler.hasClientAuthExtendedKeyUsage(certificate));
    }

    @Test
    public void shouldAllowCertificateWithAnyExtendedKeyUsage() throws Exception {
        // given - anyExtendedKeyUsage permits every purpose, including clientAuth
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getExtendedKeyUsage()).thenReturn(Collections.singletonList(ANY_EKU_OID));

        // then
        assertTrue(MTLSAuthenticationHandler.hasClientAuthExtendedKeyUsage(certificate));
    }

    @Test
    public void shouldRejectCertificateWithServerAuthOnlyExtendedKeyUsage() throws Exception {
        // given - an EKU present but scoped to serverAuth only must NOT authenticate a client
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getExtendedKeyUsage()).thenReturn(Collections.singletonList(SERVER_AUTH_OID));

        // then
        assertFalse(MTLSAuthenticationHandler.hasClientAuthExtendedKeyUsage(certificate));
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

    @Test
    public void shouldRejectAttackerLeafWithTheTrustedCertificateAuthorityAppended() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued attackerCa = TestCertificates.certificateAuthority("Attacker CA");
        TestCertificates.Issued attackerLeaf = TestCertificates.clientCertificate("attacker", attackerCa);

        assertNotAuthenticated(handler(trustedCa.certificate), attackerLeaf.certificate, trustedCa.certificate);
    }

    @Test
    public void shouldRejectSelfSignedLeafWithTheTrustedCertificateAuthorityAppended() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued selfSigned = TestCertificates.selfSignedClientCertificate("self-signed");

        assertNotAuthenticated(handler(trustedCa.certificate), selfSigned.certificate, trustedCa.certificate);
    }

    @Test
    public void shouldRejectAttackerLeafFollowedByAnotherCallersValidCertificate() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued victim = TestCertificates.clientCertificate("victim", trustedCa);
        TestCertificates.Issued attackerLeaf = TestCertificates.selfSignedClientCertificate("attacker");

        assertNotAuthenticated(handler(trustedCa.certificate), attackerLeaf.certificate, victim.certificate, trustedCa.certificate);
    }

    @Test
    public void shouldRejectTheTrustedCertificateAuthorityPresentedAlone() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");

        assertNotAuthenticated(handler(trustedCa.certificate), trustedCa.certificate);
    }

    @Test
    public void shouldRejectAnIntermediateCertificateAuthorityPresentedAsTheLeaf() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued intermediate = TestCertificates.intermediate("Intermediate CA", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30));

        assertNotAuthenticated(handler(trustedCa.certificate), intermediate.certificate, trustedCa.certificate);
    }

    @Test
    public void shouldRejectSelfSignedLeaf() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued selfSigned = TestCertificates.selfSignedClientCertificate("self-signed");

        assertNotAuthenticated(handler(trustedCa.certificate), selfSigned.certificate);
    }

    @Test
    public void shouldRejectSelfSignedLeafEvenWhenItIsConfiguredAsATrustAnchor() throws Exception {
        TestCertificates.Issued selfSigned = TestCertificates.selfSignedClientCertificate("self-signed");

        assertNotAuthenticated(handler(selfSigned.certificate), selfSigned.certificate);
    }

    @Test
    public void shouldRejectLeafSignedByAnotherCertificateAuthority() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued otherCa = TestCertificates.certificateAuthority("Other CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", otherCa);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate, otherCa.certificate);
    }

    @Test
    public void shouldRejectLeafWithoutClientAuthExtendedKeyUsage() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30), KeyPurposeId.id_kp_serverAuth);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate);
    }

    @Test
    public void shouldRejectLeafWhoseKeyUsageDoesNotPermitDigitalSignature() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificateWithKeyUsage("client", trustedCa, KeyUsage.keyEncipherment);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate);
    }

    @Test
    public void shouldAuthenticateLeafWhoseKeyUsageIncludesDigitalSignature() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificateWithKeyUsage("client", trustedCa, KeyUsage.digitalSignature | KeyUsage.keyEncipherment);

        assertThat(handler(trustedCa.certificate).controlPlaneRequestAuthenticated(requestWithChain(leaf.certificate)), is(true));
    }

    @Test
    public void shouldRejectExpiredLeaf() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa, TestCertificates.daysFromNow(-10), TestCertificates.daysFromNow(-1), KeyPurposeId.id_kp_clientAuth);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate);
    }

    @Test
    public void shouldRejectNotYetValidLeaf() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa, TestCertificates.daysFromNow(1), TestCertificates.daysFromNow(10), KeyPurposeId.id_kp_clientAuth);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate);
    }

    @Test
    public void shouldRejectLeafWhoseIntermediateIsExpired() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued intermediate = TestCertificates.intermediate("Intermediate CA", trustedCa, TestCertificates.daysFromNow(-10), TestCertificates.daysFromNow(-1));
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", intermediate);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate, intermediate.certificate);
    }

    @Test
    public void shouldRejectLeafWhoseIntermediateIsNotPresented() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued intermediate = TestCertificates.intermediate("Intermediate CA", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30));
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", intermediate);

        assertNotAuthenticated(handler(trustedCa.certificate), leaf.certificate);
    }

    @Test
    public void shouldAuthenticateValidLeafAndReturnItsVerifiedIdentity() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa);

        AuthenticationResult result = handler(trustedCa.certificate).authenticate(requestWithChain(leaf.certificate));

        assertThat(result.isAuthenticated(), is(true));
        assertThat(result.getPrincipal(), is("CN=client"));
        assertThat(result.getPrincipalSource(), is(MTLSAuthenticationHandler.PRINCIPAL_SOURCE));
        assertThat(result.getClaims().get(MTLSAuthenticationHandler.SUBJECT_DISTINGUISHED_NAME_CLAIM), is("CN=client"));
        assertThat(result.getClaims().get(MTLSAuthenticationHandler.ISSUER_DISTINGUISHED_NAME_CLAIM), is("CN=Trusted CA"));
        assertThat(result.getClaims().get(MTLSAuthenticationHandler.SERIAL_NUMBER_CLAIM), is(leaf.certificate.getSerialNumber().toString()));
        assertThat(result.getClaims().get(MTLSAuthenticationHandler.SHA256_FINGERPRINT_CLAIM), is(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.certificate.getEncoded()))));
        assertThat(result.getScopes(), is(empty()));
    }

    @Test
    public void shouldAuthenticateValidLeafWithoutExtendedKeyUsage() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30));

        assertThat(handler(trustedCa.certificate).controlPlaneRequestAuthenticated(requestWithChain(leaf.certificate)), is(true));
    }

    @Test
    public void shouldAuthenticateValidLeafWithPresentedIntermediate() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued intermediate = TestCertificates.intermediate("Intermediate CA", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30));
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", intermediate);

        AuthenticationResult result = handler(trustedCa.certificate).authenticate(requestWithChain(leaf.certificate, intermediate.certificate, trustedCa.certificate));

        assertThat(result.isAuthenticated(), is(true));
        assertThat(result.getPrincipal(), is("CN=client"));
    }

    @Test
    public void shouldAuthenticateValidLeafSignedByAConfiguredIntermediate() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued intermediate = TestCertificates.intermediate("Intermediate CA", trustedCa, TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30));
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", intermediate);

        assertThat(handler(intermediate.certificate).controlPlaneRequestAuthenticated(requestWithChain(leaf.certificate)), is(true));
    }

    @Test
    public void shouldRejectChainWhoseLeafCouldNotBeDecoded() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued victim = TestCertificates.clientCertificate("victim", trustedCa);
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            new Certificate[]{new UndecodableCertificate(), victim.certificate, trustedCa.certificate}
        );

        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> handler(trustedCa.certificate).authenticate(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates found"));
    }

    @Test
    public void shouldNameTheCallerByTheVerifiedCertificatesFingerprint() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued leaf = TestCertificates.clientCertificate("client", trustedCa);
        MTLSAuthenticationHandler handler = handler(trustedCa.certificate);
        HttpRequest request = requestWithChain(leaf.certificate);
        String expected = "mtls:sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.certificate.getEncoded()));

        assertThat(handler.principalIdentity(request, handler.authenticate(request)), is(expected));
        assertThat("verified again when the result came from another handler", handler.principalIdentity(request, AuthenticationResult.authenticated("alice", "verified-oidc", java.util.Map.of(), java.util.Set.of())), is(expected));
    }

    @Test
    public void shouldGiveTwoCertificatesWithTheSameSubjectDifferentIdentities() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued first = TestCertificates.clientCertificate("client", trustedCa);
        TestCertificates.Issued second = TestCertificates.clientCertificate("client", trustedCa);
        MTLSAuthenticationHandler handler = handler(trustedCa.certificate);

        assertThat(handler.principalIdentity(requestWithChain(first.certificate), null), not(handler.principalIdentity(requestWithChain(second.certificate), null)));
    }

    @Test
    public void shouldNameNoCallerForAChainThatDoesNotAuthenticate() throws Exception {
        TestCertificates.Issued trustedCa = TestCertificates.certificateAuthority("Trusted CA");
        TestCertificates.Issued victim = TestCertificates.clientCertificate("victim", trustedCa);
        TestCertificates.Issued attackerLeaf = TestCertificates.selfSignedClientCertificate("victim");

        assertThat(handler(trustedCa.certificate).principalIdentity(requestWithChain(attackerLeaf.certificate, victim.certificate), null), is(nullValue()));
    }

    private static MTLSAuthenticationHandler handler(X509Certificate... trusted) {
        return new MTLSAuthenticationHandler(mockServerLogger, trusted);
    }

    private static HttpRequest requestWithChain(X509Certificate... presented) {
        return new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(request(), presented);
    }

    private static void assertNotAuthenticated(MTLSAuthenticationHandler handler, X509Certificate... presented) {
        HttpRequest request = requestWithChain(presented);
        AuthenticationException authenticate = assertThrows(AuthenticationException.class, () -> handler.authenticate(request));
        assertThat(authenticate.getMessage(), equalTo(NOT_VALIDATED));
        AuthenticationException controlPlaneRequestAuthenticated = assertThrows(AuthenticationException.class, () -> handler.controlPlaneRequestAuthenticated(request));
        assertThat(controlPlaneRequestAuthenticated.getMessage(), equalTo(NOT_VALIDATED));
    }

    private static final class UndecodableCertificate extends Certificate {
        UndecodableCertificate() {
            super("X.509");
        }

        @Override
        public byte[] getEncoded() {
            return new byte[]{0x30, 0x03, 0x02, 0x01, 0x00};
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "undecodable";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }
    }

}
