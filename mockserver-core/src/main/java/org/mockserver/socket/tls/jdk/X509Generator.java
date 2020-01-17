package org.mockserver.socket.tls.jdk;

import com.google.common.net.InetAddresses;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;
import sun.security.x509.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
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

/**
 * @author jamesdbloom
 */
public class X509Generator {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String BEGIN_RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
    private static final String END_RSA_PRIVATE_KEY = "-----END RSA PRIVATE KEY-----";

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
                    .setValidityInMillis(2 * 365L)
            );
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName("test.common.name")
            .setKeyPairSize(DEFAULT_KEY_PAIR_LENGTH)
            .setSubjectAlternativeNames(Collections.singletonList("test.common.name"))
            .setValidityInMillis(DAYS.toMillis(365));
        X509AndPrivateKey pemKeyPair = x509Generator.generateSignedEphemeralCertificate(csr, rootKeyPair.getPrivateKey());
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

    public X509AndPrivateKey generateSignedEphemeralCertificate(final CertificateSigningRequest csr, final String caPrivateKey) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, InvalidKeySpecException {
        final PrivateKey privateKey = KeyFactory
            .getInstance(csr.getKeyPairAlgorithm())
            .generatePrivate(keySpecFromPEM(caPrivateKey));
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
        LocalDateTime until = since.plus(Duration.ofMillis(csr.getValidityInMillis()));

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
            .setPrivateKey(privateKeyToPEM(keyPair.getPrivate().getEncoded()))
            .setCert(certToPEM(cert.getEncoded()));
    }

    static String privateKeyToPEM(final byte[] privateKey) {
        return toPEM(privateKey, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
    }

    static String certToPEM(final byte[] key) {
        return toPEM(key, BEGIN_CERTIFICATE, END_CERTIFICATE);
    }

    private static String toPEM(final byte[] key, final String begin, final String end) {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, System.lineSeparator().getBytes());
        return begin +
            System.lineSeparator() +
            encoder.encodeToString(key) +
            System.lineSeparator() +
            end;
    }

    static byte[] privateKeyBytesFromPEM(final String pem) {
        return Base64
            .getMimeDecoder()
            .decode(
                pem
                    .replaceFirst(BEGIN_PRIVATE_KEY, EMPTY)
                    .replaceFirst(BEGIN_RSA_PRIVATE_KEY, EMPTY)
                    .replaceFirst(END_PRIVATE_KEY, EMPTY)
                    .replaceFirst(END_RSA_PRIVATE_KEY, EMPTY)
            );
    }

    public static KeySpec keySpecFromPEM(final String pem) {
        return new PKCS8EncodedKeySpec(privateKeyBytesFromPEM(pem));
    }

    public static RSAPrivateKey privateKeyFromPEMFile(String filename) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpecFromPEM(FileReader.readFileFromClassPathOrPath(filename)));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading private key from PEM file", e);
        }
    }

    public static X509Certificate x509FromPEMFile(String filename) {
        try {
            return x509FromPEM(FileReader.openStreamToFileFromClassPathOrPath(filename));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM file " + filename, e);
        }
    }

    public static X509Certificate x509FromPEM(String pem) {
        try {
            return x509FromPEM(new ByteArrayInputStream(pem.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM \n" + pem, e);
        }
    }

    private static X509Certificate x509FromPEM(InputStream inputStream) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Exception creating X509 from PEM", e);
        }
    }

    public static boolean validX509PEMFileExists(String filename) {
        try {
            return x509FromPEMFile(filename) != null;
        } catch (Exception e) {
            return false;
        }
    }
}

