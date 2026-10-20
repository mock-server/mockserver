package org.mockserver.mappers;

import io.netty.handler.ssl.util.LazyX509Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.X509Certificate;
import org.mockserver.socket.tls.PEMToFile;
import org.slf4j.event.Level;

import java.io.ByteArrayInputStream;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The client certificate chain is fixed for a connection's lifetime and the same {@code Certificate[]}
 * instance is handed to the mapper for every request on it, so the fields are extracted once per chain.
 * These tests pin that the extracted fields are identical to a full DER re-parse for every certificate
 * implementation a TLS engine can hand over, and that every request still receives its own model objects.
 */
public class JDKCertificateToMockServerX509CertificateMemoTest {

    private static final String LEAF = "org/mockserver/authentication/mtls/leaf-cert.pem";
    private static final String CA = "org/mockserver/authentication/mtls/ca.pem";

    @BeforeClass
    public static void registerBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void shouldExtractTheSameFieldsAsAFullReParseForJdkEngineCertificates() throws Exception {
        assertSameAsReParse(jdkChain());
    }

    @Test
    public void shouldExtractTheSameFieldsAsAFullReParseForOpenSslEngineCertificates() throws Exception {
        Certificate[] jdk = jdkChain();
        Certificate[] lazy = new Certificate[jdk.length];
        for (int i = 0; i < jdk.length; i++) {
            lazy[i] = new LazyX509Certificate(jdk[i].getEncoded());
        }
        assertSameAsReParse(lazy);
    }

    @Test
    public void shouldExtractTheSameFieldsAsAFullReParseForBouncyCastleCertificates() throws Exception {
        Certificate[] jdk = jdkChain();
        Certificate[] bouncyCastle = new Certificate[jdk.length];
        for (int i = 0; i < jdk.length; i++) {
            bouncyCastle[i] = new JcaX509CertificateConverter().setProvider("BC").getCertificate(new X509CertificateHolder(jdk[i].getEncoded()));
        }
        assertSameAsReParse(bouncyCastle);
    }

    @Test
    public void shouldExtractFieldsOncePerChainButBuildFreshModelObjectsPerRequest() throws Exception {
        java.security.cert.X509Certificate leaf = (java.security.cert.X509Certificate) jdkChain()[0];
        java.security.cert.X509Certificate counting = mock(java.security.cert.X509Certificate.class, AdditionalAnswers.delegatesTo(leaf));
        Certificate[] connectionChain = {counting};
        JDKCertificateToMockServerX509Certificate converter = new JDKCertificateToMockServerX509Certificate(new MockServerLogger());

        List<X509Certificate> first = converter.setClientCertificates(request(), connectionChain).getClientCertificateChain();
        List<X509Certificate> second = converter.setClientCertificates(request(), connectionChain).getClientCertificateChain();
        List<X509Certificate> third = converter.setClientCertificates(request(), connectionChain).getClientCertificateChain();

        // not a class the default X.509 factory produces, so it is re-parsed from its encoding: once for
        // the chain, plus the per-request DER copy each model object takes
        verify(counting, times(1 + 3)).getEncoded();
        assertThat(second, is(equalTo(first)));
        assertThat(third, is(equalTo(first)));
        assertThat(second, is(not(sameInstance(first))));
        assertThat(second.get(0), is(not(sameInstance(first.get(0)))));

        // one request's model is its own: mutating it must not reach another request's
        first.get(0).withSerialNumber("tampered").withSubjectDistinguishedName("CN=tampered");
        List<X509Certificate> fourth = converter.setClientCertificates(request(), connectionChain).getClientCertificateChain();
        assertThat(fourth.get(0).getSerialNumber(), is(leaf.getSerialNumber().toString()));
        assertThat(fourth.get(0).getSubjectDistinguishedName(), is(leaf.getSubjectX500Principal().getName()));
    }

    @Test
    public void shouldReExtractWhenTheConnectionChainChanges() throws Exception {
        Certificate[] leafOnly = PEMToFile.x509ChainFromPEMFile(LEAF).toArray(new Certificate[0]);
        Certificate[] caOnly = PEMToFile.x509ChainFromPEMFile(CA).toArray(new Certificate[0]);
        JDKCertificateToMockServerX509Certificate converter = new JDKCertificateToMockServerX509Certificate(new MockServerLogger());

        // a different array (a new connection)
        assertThat(converter.setClientCertificates(request(), leafOnly).getClientCertificateChain(), is(reParsed(leafOnly)));
        assertThat(converter.setClientCertificates(request(), caOnly).getClientCertificateChain(), is(reParsed(caOnly)));

        // the SAME array whose element was replaced in place
        Certificate[] mutable = {leafOnly[0]};
        assertThat(converter.setClientCertificates(request(), mutable).getClientCertificateChain(), is(reParsed(new Certificate[]{leafOnly[0]})));
        mutable[0] = caOnly[0];
        assertThat(converter.setClientCertificates(request(), mutable).getClientCertificateChain(), is(reParsed(new Certificate[]{caOnly[0]})));
    }

    @Test
    public void shouldSkipAndLogAnUndecodableCertificateOnEveryRequest() throws Exception {
        Certificate[] jdk = jdkChain();
        Certificate undecodable = new UndecodableCertificate();
        Certificate[] connectionChain = {jdk[0], undecodable};
        InfoCapturingLogger logger = new InfoCapturingLogger();
        JDKCertificateToMockServerX509Certificate converter = new JDKCertificateToMockServerX509Certificate(logger);

        HttpRequest firstRequest = request("/first");
        HttpRequest secondRequest = request("/second");
        List<X509Certificate> first = converter.setClientCertificates(firstRequest, connectionChain).getClientCertificateChain();
        List<X509Certificate> second = converter.setClientCertificates(secondRequest, connectionChain).getClientCertificateChain();

        assertThat(first, is(reParsed(new Certificate[]{jdk[0]})));
        assertThat(second, is(reParsed(new Certificate[]{jdk[0]})));
        assertThat(logger.decodeFailures, hasSize(2));
        assertThat(logger.decodeFailures.get(0).getHttpRequest(), is(sameInstance(firstRequest)));
        assertThat(logger.decodeFailures.get(1).getHttpRequest(), is(sameInstance(secondRequest)));
    }

    @Test
    public void shouldStopAtTheFirstUndecodableCertificateSoNoCertificateMovesIntoItsPosition() throws Exception {
        Certificate[] jdk = jdkChain();
        JDKCertificateToMockServerX509Certificate converter = new JDKCertificateToMockServerX509Certificate(new InfoCapturingLogger());

        assertThat(converter.setClientCertificates(request(), new Certificate[]{new UndecodableCertificate(), jdk[0], jdk[1]}).getClientCertificateChain(), is(nullValue()));
        assertThat(converter.setClientCertificates(request(), new Certificate[]{jdk[0], new UndecodableCertificate(), jdk[1]}).getClientCertificateChain(), is(reParsed(new Certificate[]{jdk[0]})));
    }

    private static void assertSameAsReParse(Certificate[] chain) throws Exception {
        JDKCertificateToMockServerX509Certificate converter = new JDKCertificateToMockServerX509Certificate(new MockServerLogger());
        List<X509Certificate> expected = reParsed(chain);
        for (int request = 0; request < 3; request++) {
            List<X509Certificate> actual = converter.setClientCertificates(request(), chain).getClientCertificateChain();
            assertThat(actual, hasSize(expected.size()));
            for (int i = 0; i < expected.size(); i++) {
                assertThat(actual.get(i).getSerialNumber(), is(expected.get(i).getSerialNumber()));
                assertThat(actual.get(i).getIssuerDistinguishedName(), is(expected.get(i).getIssuerDistinguishedName()));
                assertThat(actual.get(i).getSubjectDistinguishedName(), is(expected.get(i).getSubjectDistinguishedName()));
                assertThat(actual.get(i).getSignatureAlgorithmName(), is(expected.get(i).getSignatureAlgorithmName()));
                assertThat(actual.get(i).getCertificateBytes(), is(expected.get(i).getCertificateBytes()));
                assertThat(actual.get(i).getCertificate(), is(sameInstance(chain[i])));
                assertThat(actual.get(i).toString(), is(expected.get(i).toString()));
            }
        }
    }

    /**
     * The mapping this class performed before extraction was memoised: a full DER re-parse through the
     * default {@code X.509} {@link CertificateFactory}, per certificate, per request.
     */
    private static List<X509Certificate> reParsed(Certificate[] chain) throws Exception {
        List<X509Certificate> result = new ArrayList<>();
        for (Certificate certificate : chain) {
            java.security.cert.X509Certificate parsed = (java.security.cert.X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
            result.add(new X509Certificate()
                .withSerialNumber(parsed.getSerialNumber().toString())
                .withIssuerDistinguishedName(parsed.getIssuerX500Principal().getName())
                .withSubjectDistinguishedName(parsed.getSubjectX500Principal().getName())
                .withSignatureAlgorithmName(parsed.getSigAlgName())
                .withCertificate(certificate));
        }
        return result;
    }

    private static Certificate[] jdkChain() {
        List<Certificate> chain = new ArrayList<>(PEMToFile.x509ChainFromPEMFile(LEAF));
        chain.addAll(PEMToFile.x509ChainFromPEMFile(CA));
        return chain.toArray(new Certificate[0]);
    }

    private static final class UndecodableCertificate extends Certificate {
        UndecodableCertificate() {
            super("X.509");
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
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

    private static final class InfoCapturingLogger extends MockServerLogger {
        private final List<LogEntry> decodeFailures = new CopyOnWriteArrayList<>();

        InfoCapturingLogger() {
            super(configuration().logLevel("INFO"), JDKCertificateToMockServerX509CertificateMemoTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            if (logEntry.getLogLevel() == Level.INFO && String.valueOf(logEntry.getMessageFormat()).startsWith("exception decoding client certificate")) {
                decodeFailures.add(logEntry);
            }
        }
    }
}
