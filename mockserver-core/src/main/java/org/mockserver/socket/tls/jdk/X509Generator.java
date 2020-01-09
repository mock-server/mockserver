package org.mockserver.socket.tls.jdk;

import com.google.common.net.InetAddresses;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;
import sun.security.x509.*;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collector;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;

public class X509Generator {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

    private final MockServerLogger mockServerLogger;

    public X509Generator(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("ROOT_CA_KEY_PAIR", "<copy keypair document value for relavent environment here>");

        X509Generator x509Generator = new X509Generator(new MockServerLogger());
        X509AndPrivateKey rootKeyPair = x509Generator
            .generateRootKeyPair(
                new CertificateSigningRequest()
                    .setCommonName(ROOT_COMMON_NAME)
                    .setKeyPairSize(DEFAULT_KEY_PAIR_LENGTH)
                    .setValidity(2 * 365L)
            );
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName("test.common.name")
            .setKeyPairSize(DEFAULT_KEY_PAIR_LENGTH)
            .setSubjectAlternativeNames(Collections.singletonList("test.common.name"))
            .setValidity(DAYS.toMillis(365));
        X509AndPrivateKey pemKeyPair = x509Generator.generateSignedEphemeralCertificate(csr, rootKeyPair);
        System.out.println("keyPair:" + NEW_LINE +
            ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(pemKeyPair)
        );
    }

    public X509AndPrivateKey generateRootKeyPair(final CertificateSigningRequest csr) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException {
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subjectAndIssuer = new X500Name(buildDistinguishedName(csr.getCommonName()));
        X509CertInfo x509CertInfo = buildX509CertInfo(subjectAndIssuer, subjectAndIssuer, keyPair.getPublic(), csr);
        updateWithRootCertificateExtensions(x509CertInfo);
        return signX509KeyPair(keyPair.getPrivate(), keyPair, x509CertInfo, csr.getSigningAlgorithm());
    }

    public X509AndPrivateKey generateSignedEphemeralCertificate(final CertificateSigningRequest csr, final X509AndPrivateKey rootKeyPair) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, InvalidKeySpecException {
        final PrivateKey privateKey = KeyFactory
            .getInstance(csr.getKeyPairAlgorithm())
            .generatePrivate(new PKCS8EncodedKeySpec(privateKeyFromPem(rootKeyPair.getPrivateKey())));
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subject = new X500Name(buildDistinguishedName(csr.getCommonName()));
        final X500Name issuer = new X500Name(buildDistinguishedName(ROOT_COMMON_NAME));
        X509CertInfo x509CertInfo = buildX509CertInfo(subject, issuer, keyPair.getPublic(), csr);
        updateWithCertificateExtensions(x509CertInfo, csr.getSubjectAlternativeNames());
        return signX509KeyPair(privateKey, keyPair, x509CertInfo, csr.getSigningAlgorithm());
    }

    private KeyPair generateKeyPair(final String algorithm, final int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg;
        kpg = KeyPairGenerator.getInstance(algorithm);
        kpg.initialize(keySize);
        return kpg.genKeyPair();
    }

    private X509CertInfo buildX509CertInfo(final X500Name subject, final X500Name issuer, final PublicKey publicKey, final CertificateSigningRequest csr) throws IOException, NoSuchAlgorithmException, CertificateException {
        X509CertInfo x509CertInfo = new X509CertInfo();

        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        LocalDateTime until = since.plus(Duration.ofMillis(csr.getValidity()));

        CertificateValidity interval = new CertificateValidity(
            Date.from(since.atZone(ZoneId.systemDefault()).toInstant()),
            Date.from(until.atZone(ZoneId.systemDefault()).toInstant())
        );
        // replaced secure random with random in order to prevent entropy depletion
        BigInteger sn = new BigInteger(64, new Random());

        x509CertInfo.set(X509CertInfo.VALIDITY, interval);
        x509CertInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        x509CertInfo.set(X509CertInfo.SUBJECT, subject);
        x509CertInfo.set(X509CertInfo.ISSUER, issuer);
        x509CertInfo.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));
        x509CertInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

        AlgorithmId algo = new AlgorithmId(AlgorithmId.get(csr.getSigningAlgorithm()).getOID());
        x509CertInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        return x509CertInfo;
    }

    private void updateWithCertificateExtensions(final X509CertInfo x509CertInfo, final List<String> subjectAlternativeNames) throws IOException, CertificateException {
        CertificateExtensions certificateExtensions = new CertificateExtensions();

        GeneralNames generalNames = subjectAlternativeNames
            .stream()
            .map(this::buildGeneralName)
            .filter(Objects::nonNull)
            .collect(
                Collector.of(
                    GeneralNames::new,
                    GeneralNames::add,
                    ((generalNames1, generalNames2) -> null //do nothing
                    )
                )
            );

        if (!generalNames.isEmpty()) {
            certificateExtensions.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(Boolean.FALSE, generalNames));
        }

        x509CertInfo.set(X509CertInfo.EXTENSIONS, certificateExtensions);
    }

    private void updateWithRootCertificateExtensions(final X509CertInfo x509CertInfo) throws IOException, CertificateException {
        CertificateExtensions certificateExtensions = new CertificateExtensions();

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.9
        certificateExtensions.set(
            BasicConstraintsExtension.NAME,
            new BasicConstraintsExtension(
                true, // is critical
                true, // is CA
                -1 // path length
            )
        );

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.3
        boolean[] keyUsage = new boolean[9];
        keyUsage[5] = true; // keyCertSign

        certificateExtensions.set(KeyUsageExtension.NAME, new KeyUsageExtension(keyUsage));

        x509CertInfo.set(X509CertInfo.EXTENSIONS, certificateExtensions);
    }


    @SuppressWarnings("UnstableApiUsage")
    private GeneralName buildGeneralName(final String subjectAlternativeName) {
        GeneralName gn = null;
        try {
            GeneralNameInterface gni = InetAddresses.isUriInetAddress(subjectAlternativeName) ? new IPAddressName(subjectAlternativeName) : new DNSName(subjectAlternativeName);
            gn = new GeneralName(gni);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Unable to create a subject alternative name with the value " + subjectAlternativeName + " it doesn't appear to be either a valid dns name or an IP address")
                    .setThrowable(e)
            );
        }
        return gn;
    }

    private X509AndPrivateKey signX509KeyPair(final PrivateKey privateKey, final KeyPair keyPair, X509CertInfo x509CertInfo, final String signatureAlgorithm) throws CertificateException, NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException, SignatureException {
        x509CertInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get(signatureAlgorithm)));
        X509CertImpl cert = new X509CertImpl(x509CertInfo);
        cert.sign(privateKey, signatureAlgorithm);
        return new X509AndPrivateKey()
            .setPrivateKey(privateKeyToPem(keyPair.getPrivate().getEncoded()))
            .setCert(certToPem(cert.getEncoded()));
    }

    String privateKeyToPem(final byte[] privateKey) {
        return toPem(privateKey, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
    }

    String certToPem(final byte[] key) {
        return toPem(key, BEGIN_CERTIFICATE, END_CERTIFICATE);
    }

    private String toPem(final byte[] key, final String begin, final String end) {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes());
        return begin +
            System.lineSeparator() +
            encoder.encodeToString(key) +
            System.lineSeparator() +
            end;
    }

    @SuppressWarnings("unused")
    byte[] certFromPem(final String cert) {
        return Base64
            .getMimeDecoder()
            .decode(
                cert
                    .replaceFirst(X509Generator.BEGIN_CERTIFICATE, EMPTY)
                    .replaceFirst(X509Generator.END_CERTIFICATE, EMPTY)
            );
    }

    byte[] privateKeyFromPem(final String privateKey) {
        return Base64
            .getMimeDecoder()
            .decode(
                privateKey
                    .replaceFirst(BEGIN_PRIVATE_KEY, EMPTY)
                    .replaceFirst(END_PRIVATE_KEY, EMPTY)
            );
    }
}

