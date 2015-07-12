package org.mockserver.socket;

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
import org.bouncycastle.util.IPAddress;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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

    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";

    private static final String KEY_GENERATION_ALGORITHM = "RSA";

    private static final boolean REGENERATE_FRESH_CA_CERTIFICATE = false;

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
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
     * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
     * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT (issue #6).
     * 
     * Hundred years in the future from starting the proxy should be enough.
     */
    private static final Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    public static KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM, PROVIDER_NAME);
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
            for (String subjectAlternativeNameDomain : subjectAlternativeNameDomains) {
                subjectAlternativeNames.add(new GeneralName(GeneralName.dNSName, subjectAlternativeNameDomain));
            }
        }
        if (subjectAlternativeNameIps != null) {
            for (String subjectAlternativeNameIp : subjectAlternativeNameIps) {
                if (IPAddress.isValidIPv6WithNetmask(subjectAlternativeNameIp)
                        || IPAddress.isValidIPv6(subjectAlternativeNameIp)
                        || IPAddress.isValidIPv4WithNetmask(subjectAlternativeNameIp)
                        || IPAddress.isValidIPv4(subjectAlternativeNameIp)) {
                    subjectAlternativeNames.add(new GeneralName(GeneralName.iPAddress, subjectAlternativeNameIp));
                }
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
    KeyStore generateCertificate(KeyStore keyStore, String certificationAlias, String certificateAuthorityAlias, char[] keyStorePassword, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        //
        // personal keys
        //
        KeyPair keyPair = generateKeyPair(FAKE_KEYSIZE);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        //
        // ca keys
        //
        PrivateKey caPrivateKey = loadPrivateKeyFromPEMFile("org/mockserver/socket/CertificateAuthorityPrivateKey.pem");
        X509Certificate caCert = (X509Certificate) loadCertificateFromKeyStore("org/mockserver/socket/CertificateAuthorityKeyStore.jks", certificateAuthorityAlias, keyStorePassword);

        //
        // regenerate ca private key and ca certificate (for development / debugging only)
        //
        if (REGENERATE_FRESH_CA_CERTIFICATE) {
            KeyPair caKeyPair = generateKeyPair(ROOT_KEYSIZE);
            PublicKey caPublicKey = caKeyPair.getPublic();
            caPrivateKey = caKeyPair.getPrivate();
            caCert = createCACert(caPublicKey, caPrivateKey);

            KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(readFileFromClassPathOrPath("org/mockserver/socket/CertificateAuthorityKeyStore.jks"), ConfigurationProperties.javaKeyStorePassword().toCharArray());
            saveCertificateAsKeyStore(
                    caKeyStore,
                    false,
                    "CertificateAuthorityKeyStore.jks",
                    certificateAuthorityAlias,
                    privateKey,
                    keyStorePassword,
                    new X509Certificate[]{caCert},
                    caCert
            );
            saveCertificateAsPEMFile(caCert, "CertificateAuthorityCertificate.pem");
            saveCertificateAsPEMFile(caPublicKey, "CertificateAuthorityPublicKey.pem");
            saveCertificateAsPEMFile(caPrivateKey, "CertificateAuthorityPrivateKey.pem");
        }

        //
        // generate client certificate
        //
        X509Certificate clientCert = createClientCert(publicKey, caCert, caPrivateKey, caCert.getPublicKey(), domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);

        return saveCertificateAsKeyStore(
                keyStore,
                ConfigurationProperties.deleteGeneratedKeyStoreOnExit(),
                ConfigurationProperties.javaKeyStoreFilePath(),
                certificationAlias,
                privateKey,
                keyStorePassword,
                new X509Certificate[]{clientCert, caCert},
                caCert
        );
    }

    /**
     * Saves X509Certificate as Base-64 encoded PEM file.
     */
    public void saveCertificateAsPEMFile(Object x509Certificate, String filename) throws IOException {
        FileWriter fileWriter = new FileWriter(filename);
        JcaPEMWriter jcaPEMWriter = null;
        try {
            jcaPEMWriter = new JcaPEMWriter(fileWriter);
            jcaPEMWriter.writeObject(x509Certificate);
        } finally {
            IOUtils.closeQuietly(jcaPEMWriter);
            IOUtils.closeQuietly(fileWriter);
        }
    }

    /**
     * Save X509Certificate in KeyStore file.
     */
    private KeyStore saveCertificateAsKeyStore(KeyStore existingKeyStore, boolean deleteOnExit, String keyStoreFileName, String certificationAlias, Key privateKey, char[] keyStorePassword, Certificate[] chain, X509Certificate caCert) {
        try {
            KeyStore keyStore = existingKeyStore;
            if (keyStore == null) {
                // create new key store
                keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, keyStorePassword);
            }

            // add certificate
            try {
                keyStore.deleteEntry(certificationAlias);
            } catch (KeyStoreException kse) {
                // ignore as may not exist in keystore yet
            }
            keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, chain);

            // add CA certificate
            try {
                keyStore.deleteEntry(SSLFactory.KEY_STORE_CA_ALIAS);
            } catch (KeyStoreException kse) {
                // ignore as may not exist in keystore yet
            }
            keyStore.setCertificateEntry(SSLFactory.KEY_STORE_CA_ALIAS, caCert);

            // save as JKS file
            File keyStoreFile = new File(keyStoreFileName);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(keyStoreFile);
                keyStore.store(fileOutputStream, keyStorePassword);
                logger.trace("Saving key store to file [" + keyStoreFileName + "]");
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }
            if (deleteOnExit) {
                keyStoreFile.deleteOnExit();
            }
            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

    /**
     * Load PrivateKey from PEM file.
     */
    private RSAPrivateKey loadPrivateKeyFromPEMFile(String privateKeyFileName) {
        try {
            String publicKeyFile = IOUtils.toString(new InputStreamReader(KeyStoreFactory.class.getClassLoader().getResourceAsStream(privateKeyFileName)));
            byte[] publicKeyBytes = DatatypeConverter.parseBase64Binary(publicKeyFile.replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", ""));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading private key from PEM file", e);
        }
    }

    /**
     * Load X509Certificate from KeyStore file.
     */
    private Certificate loadCertificateFromKeyStore(String keyStoreFileName, String certificationAlias, char[] keyStorePassword) {
        try {
            InputStream inputStream = readFileFromClassPathOrPath(keyStoreFileName);
            try {
                logger.trace("Loading key store from file [" + keyStoreFileName + "]");
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(inputStream, keyStorePassword);
                return keystore.getCertificate(certificationAlias);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFileName, e);
        }
    }

    /**
     * Load file from classpath and if not found then try file path
     */
    private InputStream readFileFromClassPathOrPath(String keyStoreFileName) throws FileNotFoundException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keyStoreFileName);
        if (inputStream == null) {
            // load from path if not found in classpath
            inputStream = new FileInputStream(keyStoreFileName);
        }
        return inputStream;
    }

}
