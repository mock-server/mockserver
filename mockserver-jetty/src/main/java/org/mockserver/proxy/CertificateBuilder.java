package org.mockserver.proxy;

import sun.security.x509.*;

import java.io.IOException;
import java.math.BigInteger;
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
public class CertificateBuilder {

    /**
     * Create KeyStore and add a self-signed X.509 Certificate
     *
     * @param dname the X.509 Distinguished Name, eg "CN=www.google.co.uk, O=\"Google Inc\", L=\"Mountain View\", S=California, C=US"
     * @param keyAlgorithmName the key algorithm, eg "RSA"
     */
    public static KeyStore generateCertificate(String alias, char[] keyStorePassword, KeyAlgorithmName keyAlgorithmName, String dname, String... sanDomains)
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
