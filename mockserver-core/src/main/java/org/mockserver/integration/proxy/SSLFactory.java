package org.mockserver.integration.proxy;

import sun.security.x509.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class SSLFactory {

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String KEY_STORE_FILENAME = "keystore.jks";
    private static KeyStore keystore;

    public static SSLSocket wrapSocket(Socket socket, SSLContext sslContext) throws Exception {
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        return sslSocket;
    }

    public static KeyStore buildKeyStore() {
        if (keystore == null) {
            File keyStoreFile = new File(KEY_STORE_FILENAME);
            if (keyStoreFile.exists()) {
                loadKeyStore(keyStoreFile);
            } else {
                dynamicallyCreateKeyStore();
                saveKeyStore();
            }
        }
        return keystore;
    }

    private static void dynamicallyCreateKeyStore() {
        try {
            keystore = SSLFactory.generateCertificate(
                    "certAlias",
                    KEY_STORE_PASSWORD.toCharArray(),
                    KeyAlgorithmName.RSA,
                    "CN=www.mockserver.com, O=MockServer, L=London, S=England, C=UK"
            );
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Exception while building KeyStore dynamically", e);
        }
    }

    private static void loadKeyStore(File keyStoreFile) {
        try {
            try (FileInputStream fileInputStream = new FileInputStream(KEY_STORE_FILENAME)) {
                keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(fileInputStream, KEY_STORE_PASSWORD.toCharArray());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
        }
    }

    private static void saveKeyStore() {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            keystore.store(bout, KEY_STORE_PASSWORD.toCharArray());
            File keyStoreFile = new File(KEY_STORE_FILENAME);
            try (FileOutputStream fileOutputStream = new FileOutputStream(keyStoreFile)) {
                fileOutputStream.write(bout.toByteArray());
            }
            keyStoreFile.deleteOnExit();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

    /**
     * Create KeyStore and add a self-signed X.509 Certificate
     *
     * @param dname the X.509 Distinguished Name, eg "CN=www.google.co.uk, O=\"Google Inc\", L=\"Mountain View\", S=California, C=US"
     * @param keyAlgorithmName the key algorithm, eg "RSA"
     */
    private static KeyStore generateCertificate(String alias, char[] keyStorePassword, KeyAlgorithmName keyAlgorithmName, String dname, String... sanDomains)
            throws GeneralSecurityException, IOException {

        CertAndKeyGen certAndKeyGen = new CertAndKeyGen(keyAlgorithmName.name(), keyAlgorithmName.signatureAlgorithmName, "SunCertificates");
        certAndKeyGen.generate(keyAlgorithmName.keySize);

        PrivateKey privateKey = certAndKeyGen.getPrivateKey();
        X509CertInfo info = new X509CertInfo();
        Date from = new Date();
        Date to = new Date(from.getTime() + TimeUnit.DAYS.toMillis(360));
        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger sn = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name(dname);

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
        info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
        info.set(X509CertInfo.KEY, new CertificateX509Key(certAndKeyGen.getPublicKey()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid)));

        // add subject alternative names
        GeneralNames generalNames = new GeneralNames();
        for (String sanDomain : sanDomains) {
            generalNames.add(new GeneralName(new DNSName(sanDomain)));
        }
        if (generalNames.size() > 0) {
            CertificateExtensions certificateExtensions = (CertificateExtensions) info.get(X509CertInfo.EXTENSIONS);
            if (certificateExtensions == null) certificateExtensions = new CertificateExtensions();
            certificateExtensions.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(generalNames));
            info.set(X509CertInfo.EXTENSIONS, certificateExtensions);
        }

        // Sign the certificate to identify the algorithm that's used.
        X509CertImpl x509Certificate = new X509CertImpl(info);
        x509Certificate.sign(privateKey, keyAlgorithmName.signatureAlgorithmName);

        // update the algorithm, and resign.
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, x509Certificate.get(X509CertImpl.SIG_ALG));
        x509Certificate = new X509CertImpl(info);
        x509Certificate.sign(privateKey, keyAlgorithmName.signatureAlgorithmName);

        // add to new key store
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keyStorePassword);
        keyStore.setKeyEntry(alias, privateKey, keyStorePassword, new X509Certificate[]{x509Certificate});

        return keyStore;
    }

    public enum KeyAlgorithmName {
        EC(256, "SHA256withECDSA"),
        RSA(2048, "SHA256WithRSA"),
        DSA(1024, "SHA1WithDSA");
        private final int keySize;
        private final String signatureAlgorithmName;

        KeyAlgorithmName(int keySize, String signatureAlgorithmName) {
            this.keySize = keySize;
            this.signatureAlgorithmName = signatureAlgorithmName;
        }
    }
}
