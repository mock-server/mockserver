package org.mockserver.socket.tls;

import org.mockserver.file.FileReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockserver.character.Character.NEW_LINE;

public class PEMToFile {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String BEGIN_RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
    private static final String END_RSA_PRIVATE_KEY = "-----END RSA PRIVATE KEY-----";

    public static String privateKeyToPEM(final byte[] privateKey) {
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

    public static byte[] privateKeyBytesFromPEM(final String pem) {
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
            throw new RuntimeException("Exception reading X509 from PEM " + NEW_LINE + pem, e);
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
            throw new RuntimeException("Exception reading X509 from PEM " + NEW_LINE + pem, e);
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
