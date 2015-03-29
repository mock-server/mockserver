package org.mockserver.socket;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * @author jamesdbloom, ganskef
 */
public class KeyStoreFactory {

    private static final Logger logger = LoggerFactory.getLogger(SSLFactory.class);

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSAEncryption";

    private static final String KEYGEN_ALGORITHM = "RSA";

    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the Certificate Authority.
     */
    private static final int ROOT_KEYSIZE = 2048;

    /**
     * Generates an 1024 bit RSA key pair using SHA1PRNG for the server
     * certificates. Thoughts: 2048 takes much longer time on older CPUs. And
     * for almost every client, 1024 is sufficient.
     */
    private static final int FAKE_KEYSIZE = 1024;

    /**
     * Current time minus 1 year, just in case software clock goes back due to
     * time synchronization
     */
    private static final Date NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);

    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59
     */
    private static final Date NOT_AFTER = new Date(253402300799000L);

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    public static KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM, PROVIDER_NAME);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        ASN1InputStream is = null;
        try {
            is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()));
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey signedWithPrivateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(signedWithPrivateKey);
        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
    }

    /**
     * Create a certificate to use by a Certificate Authority, signed by a self signed certificate.
     */
    public X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        //
        // signers name
        //
        X500Name issuerName = new X500Name("CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK");

        //
        // subjects name - the same as we are self signed.
        //
        X500Name subjectName = issuerName;

        //
        // serial
        //
        BigInteger serial = BigInteger.valueOf(new Random().nextInt());

        //
        // create the certificate - version 3
        //
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial, NOT_BEFORE, NOT_AFTER, subjectName, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment | KeyUsage.cRLSign);
        builder.addExtension(Extension.keyUsage, false, usage);

        ASN1EncodableVector purposes = new ASN1EncodableVector();
        purposes.add(KeyPurposeId.id_kp_serverAuth);
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.anyExtendedKeyUsage);
        builder.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

        X509Certificate cert = signCertificate(builder, privateKey);
        cert.checkValidity(new Date());
        cert.verify(publicKey);

        return cert;
    }

    /**
     * Create a server certificate for the given domain and subject alternative names, signed by the given Certificate Authority.
     */
    public X509Certificate createClientCert(PublicKey publicKey, X509Certificate certificateAuthorityCert, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        //
        // signers name
        //
        X500Name issuer = new X509CertificateHolder(certificateAuthorityCert.getEncoded()).getSubject();

        //
        // subjects name - the same as we are self signed.
        //
        X500Name subject = new X500Name("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK");

        //
        // serial
        //
        BigInteger serial = BigInteger.valueOf(new Random().nextInt());

        //
        // create the certificate - version 3
        //
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER, subject, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        //
        // subject alternative name
        //
        List<ASN1Encodable> subjectAlternativeNames = new ArrayList<ASN1Encodable>();
        if (subjectAlternativeNameDomains != null) {
            subjectAlternativeNames.add(new GeneralName(GeneralName.dNSName, domain));
            for (String subjectAlternativeName : subjectAlternativeNameDomains) {
                subjectAlternativeNames.add(new GeneralName(GeneralName.dNSName, subjectAlternativeName));
            }
        }
        if (subjectAlternativeNameIps != null) {
            for (String subjectAlternativeName : subjectAlternativeNameIps) {
                subjectAlternativeNames.add(new GeneralName(GeneralName.iPAddress, subjectAlternativeName));
            }
        }
        if (subjectAlternativeNames.size() > 0) {
            DERSequence subjectAlternativeNamesExtension = new DERSequence(subjectAlternativeNames.toArray(new ASN1Encodable[subjectAlternativeNames.size()]));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        X509Certificate cert = signCertificate(builder, certificateAuthorityPrivateKey);

        cert.checkValidity(new Date());
        cert.verify(certificateAuthorityPublicKey);

        return cert;
    }

    /**
     * Create a KeyStore with a server certificate for the given domain and subject alternative names.
     */
    KeyStore generateCertificate(String certificationAlias, String certificateAuthorityAlias, char[] keyStorePassword, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        //
        // personal keys
        //
        KeyPair keyPair = generateKeyPair(FAKE_KEYSIZE);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        //
        // ca keys
        //
        KeyPair caKeyPair = generateKeyPair(ROOT_KEYSIZE);
        PrivateKey caPrivateKey = caKeyPair.getPrivate();
        PublicKey caPublicKey = caKeyPair.getPublic();

        //
        // generate certificates
        //
        X509Certificate caCert = createCACert(caPublicKey, caPrivateKey);
        X509Certificate clientCert = createClientCert(publicKey, caCert, caPrivateKey, caPublicKey, domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);

        // save certificates as PEM files
        saveCertificateAsPEMFile(clientCert, "mockserverClientCertificate.pem");
        saveCertificateAsPEMFile(caCert, "mockserverCertificateAuthorityCertificate.pem");

        saveCertificateAsPKCS12File(certificationAlias, privateKey, keyStorePassword, new X509Certificate[]{clientCert, caCert});
        return saveCertificateAsJKSKeyStore(certificationAlias, privateKey, keyStorePassword, new X509Certificate[]{clientCert, caCert});
    }

    /**
     * Saves X509Certificate as Base-64 encoded PEM file.
     */
    public void saveCertificateAsPEMFile(X509Certificate x509Certificate, String filename) throws IOException {
        StringWriter stringWriter = new StringWriter();
        JcaPEMWriter jcaPEMWriter = null;
        try {
            jcaPEMWriter = new JcaPEMWriter(stringWriter);
            jcaPEMWriter.writeObject(x509Certificate);
        } finally {
            IOUtils.closeQuietly(jcaPEMWriter);
        }
        IOUtils.write(stringWriter.toString(), new FileOutputStream(filename), Charsets.UTF_8);
    }

    /**
     * Save X509Certificate in JKS KeyStore file.
     */
    private KeyStore saveCertificateAsJKSKeyStore(String certificationAlias, Key privateKey, char[] keyStorePassword, Certificate[] chain) {
        try {
            // create new key store
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, keyStorePassword);

            // add certification
            keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, chain);

            // save as JKS file
            File keyStoreFile = new File(ConfigurationProperties.javaKeyStoreFilePath());
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(keyStoreFile);
                keyStore.store(fileOutputStream, ConfigurationProperties.javaKeyStorePassword().toCharArray());
                logger.trace("Saving key store to file [" + ConfigurationProperties.javaKeyStoreFilePath() + "]");
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }
            keyStoreFile.deleteOnExit();

            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

    /**
     * Save X509Certificate in PKCS12 file.
     */
    public KeyStore saveCertificateAsPKCS12File(String certificationAlias, Key privateKey, char[] keyStorePassword, Certificate[] chain) throws Exception {
        try {
            // create new key store
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, keyStorePassword);

            // add certification
            keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, chain);

            // save as JKS file
            File keyStoreFile = new File(ConfigurationProperties.pkcs12KeyStoreFilePath());
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(keyStoreFile);
                keyStore.store(fileOutputStream, ConfigurationProperties.javaKeyStorePassword().toCharArray());
                logger.trace("Saving key store to file [" + ConfigurationProperties.javaKeyStoreFilePath() + "]");
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }

            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

}
