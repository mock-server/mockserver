package org.mockserver.socket.tls.jdk;

import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;
import sun.security.x509.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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

    public X509AndPrivateKey generateRootX509AndPrivateKey(final CertificateSigningRequest csr) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException {
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subjectAndIssuer = new X500Name(buildDistinguishedName(csr.getCommonName()));
        X509CertInfo x509CertInfo = buildX509CertInfo(subjectAndIssuer, subjectAndIssuer, keyPair.getPublic(), csr);
        updateWithRootCertificateExtensions(x509CertInfo);
        return signX509KeyPair(keyPair.getPrivate(), keyPair, x509CertInfo, csr.getSigningAlgorithm());
    }

    public X509AndPrivateKey generateLeafX509AndPrivateKey(final CertificateSigningRequest csr, String issuerDistinguishingName, final String caPrivateKey) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, InvalidKeySpecException {
        final PrivateKey privateKey = KeyFactory
            .getInstance(csr.getKeyPairAlgorithm())
            .generatePrivate(keySpecFromPEM(caPrivateKey));
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subject = new X500Name(buildDistinguishedName(csr.getCommonName()));
        final X500Name issuer = new X500Name(issuerDistinguishingName);
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

        CertificateValidity interval = new CertificateValidity(
            NOT_BEFORE,
            NOT_AFTER
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
            .filter(StringUtils::isNotBlank)
            .map(this::buildGeneralName)
            .filter(Objects::nonNull)
            .collect(
                Collector.of(
                    GeneralNames::new,
                    GeneralNames::add,
                    (generalNames1, generalNames2) -> null //do nothing)
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
            gn = new GeneralName(InetAddresses.isUriInetAddress(subjectAlternativeName) ? new IPAddressName(subjectAlternativeName) : new DNSName(subjectAlternativeName));
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

    public static String certToPEM(final X509Certificate... x509Certificates) throws CertificateEncodingException {
        StringBuilder pem = new StringBuilder();
        for (X509Certificate x509Certificate : x509Certificates) {
            pem.append(toPEM(x509Certificate.getEncoded(), BEGIN_CERTIFICATE, END_CERTIFICATE)).append(NEW_LINE);
        }
        return pem.toString();
    }

    public static String certToPEM(final byte[]... x509Certificates) {
        StringBuilder pem = new StringBuilder();
        for (byte[] x509Certificate : x509Certificates) {
            pem.append(toPEM(x509Certificate, BEGIN_CERTIFICATE, END_CERTIFICATE)).append(NEW_LINE);
        }
        return pem.toString();
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
        if (pem.contains(BEGIN_RSA_PRIVATE_KEY) || pem.contains(END_RSA_PRIVATE_KEY)) {
            new MockServerLogger().logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Private key provided in unsupported PKCS#1 only PKCS#8 format is support, to convert use openssl, for example{}")
                    .setArguments("openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt")
            );
        }
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
            return privateKeyFromPEM(FileReader.readFileFromClassPathOrPath(filename));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading private key from PEM file", e);
        }
    }

    public static RSAPrivateKey privateKeyFromPEM(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpecFromPEM(pem));
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

    public static List<X509Certificate> x509ChainFromPEMFile(String filename) {
        try {
            return x509ChainFromPEM(FileReader.openStreamToFileFromClassPathOrPath(filename));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM file " + filename, e);
        }
    }

    public static List<X509Certificate> x509ChainFromPEM(String pem) {
        try {
            return x509ChainFromPEM(new ByteArrayInputStream(pem.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM \n" + pem, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<X509Certificate> x509ChainFromPEM(InputStream inputStream) {
        try {
            return (List<X509Certificate>) CertificateFactory
                .getInstance("X.509")
                .generateCertificates(inputStream)
                .stream()
                .filter(certificate -> certificate instanceof X509Certificate)
                .collect(Collectors.toList());
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

