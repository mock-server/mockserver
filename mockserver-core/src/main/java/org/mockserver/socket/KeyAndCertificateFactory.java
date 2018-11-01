package org.mockserver.socket;

import org.apache.commons.lang3.StringUtils;
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
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * @author jamesdbloom, ganskef
 */
public class KeyAndCertificateFactory {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(KeyAndCertificateFactory.class);

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
    private static final String KEY_GENERATION_ALGORITHM = "RSA";
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
     * <p>
     * Hundred years in the future from starting the proxy should be enough.
     */
    private static final Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);
    private static final KeyAndCertificateFactory KEY_AND_CERTIFICATE_FACTORY = new KeyAndCertificateFactory();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private String mockServerCertificatePEMFile;
    private String mockServerPublicKeyPEMFile;
    private String mockServerPrivateKeyPEMFile;

    private KeyAndCertificateFactory() {

    }

    public static KeyAndCertificateFactory keyAndCertificateFactory() {
        return KEY_AND_CERTIFICATE_FACTORY;
    }


    public static void addSubjectAlternativeName(String host) {
        if (host != null) {
            String hostWithoutPort = StringUtils.substringBefore(host, ":");

            if (!ConfigurationProperties.containsSslSubjectAlternativeName(hostWithoutPort)) {
                try {
                    // resolve host name for subject alternative name in case host name is ip address
                    for (InetAddress addr : InetAddress.getAllByName(hostWithoutPort)) {
                        ConfigurationProperties.addSslSubjectAlternativeNameIps(addr.getHostAddress());
                        ConfigurationProperties.addSslSubjectAlternativeNameDomains(addr.getHostName());
                        ConfigurationProperties.addSslSubjectAlternativeNameDomains(addr.getCanonicalHostName());
                    }
                } catch (UnknownHostException uhe) {
                    ConfigurationProperties.addSslSubjectAlternativeNameDomains(hostWithoutPort);
                }
            }
        }
    }

    private static SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        try (ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        }
    }

    private static X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey signedWithPrivateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(signedWithPrivateKey);
        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
    }

    public static void main(String[] args) throws Exception {
        keyAndCertificateFactory().buildAndSaveCertificateAuthorityCertificates();
    }

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM, PROVIDER_NAME);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * Create a certificate to use by a Certificate Authority, signed by a self signed certificate.
     */
    private X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        // signers name
        X500Name issuerName = new X500Name("CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK");

        // subjects name - the same as we are self signed.
        X500Name subjectName = issuerName;

        // serial
        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));

        // create the certificate - version 3
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
    private X509Certificate createCASignedCert(PublicKey publicKey, X509Certificate certificateAuthorityCert, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        // signers name
        X500Name issuer = new X509CertificateHolder(certificateAuthorityCert.getEncoded()).getSubject();

        // subjects name - the same as we are self signed.
        X500Name subject = new X500Name("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK");

        // serial
        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));

        // create the certificate - version 3
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER, subject, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        // subject alternative name
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
     * Regenerate Certificate Authority public/private keys and X.509 certificate
     * <p>
     * Note: X.509 certificate should be stable so this method should rarely be used
     */
    synchronized KeyAndCertificateFactory buildAndSaveCertificateAuthorityCertificates() throws Exception {
        KeyPair caKeyPair = generateKeyPair(ROOT_KEYSIZE);

        saveCertificateAsPEMFile(createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate()), "CertificateAuthorityCertificate.pem", false);
        saveCertificateAsPEMFile(caKeyPair.getPublic(), "CertificateAuthorityPublicKey.pem", false);
        saveCertificateAsPEMFile(caKeyPair.getPrivate(), "CertificateAuthorityPrivateKey.pem", false);

        return this;
    }

    /**
     * Create a KeyStore with a server certificate for the given domain and subject alternative names.
     */
    synchronized KeyAndCertificateFactory buildAndSaveCertificates() {
        try {
            // personal keys
            KeyPair keyPair = generateKeyPair(FAKE_KEYSIZE);
            PrivateKey mockServerPrivateKey = keyPair.getPrivate();
            PublicKey mockServerPublicKey = keyPair.getPublic();

            // ca keys
            PrivateKey caPrivateKey = loadPrivateKeyFromPEMFile("org/mockserver/socket/CertificateAuthorityPrivateKey.pem");
            X509Certificate caCert = loadX509FromPEMFile("org/mockserver/socket/CertificateAuthorityCertificate.pem");

            // generate mockServer certificate
            X509Certificate mockServerCert = createCASignedCert(
                mockServerPublicKey,
                caCert,
                caPrivateKey,
                caCert.getPublicKey(),
                ConfigurationProperties.sslCertificateDomainName(),
                ConfigurationProperties.sslSubjectAlternativeNameDomains(),
                ConfigurationProperties.sslSubjectAlternativeNameIps()
            );
            String randomUUID = UUID.randomUUID().toString();
            mockServerCertificatePEMFile = saveCertificateAsPEMFile(mockServerCert, "MockServerCertificate" + randomUUID + ".pem", true);
            mockServerPublicKeyPEMFile = saveCertificateAsPEMFile(mockServerPublicKey, "MockServerPublicKey" + randomUUID + ".pem", true);
            mockServerPrivateKeyPEMFile = saveCertificateAsPEMFile(mockServerPrivateKey, "MockServerPrivateKey" + randomUUID + ".pem", true);
        } catch (Exception e) {
            MOCK_SERVER_LOGGER.error("Error while refreshing certificates", e);
        }
        return this;
    }

    /**
     * Saves X509Certificate as Base-64 encoded PEM file.
     */
    private String saveCertificateAsPEMFile(Object x509Certificate, String filename, boolean deleteOnExit) throws IOException {
        File pemFile = File.createTempFile(filename, null);
        try (FileWriter pemfileWriter = new FileWriter(pemFile)) {
            try (JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(pemfileWriter)) {
                jcaPEMWriter.writeObject(x509Certificate);
            }
        }
        if (deleteOnExit) {
            pemFile.deleteOnExit();
        }
        return pemFile.getAbsolutePath();
    }

    public PrivateKey mockServerPrivateKey() {
        return loadPrivateKeyFromPEMFile(mockServerPrivateKeyPEMFile);
    }

    public X509Certificate mockServerX509Certificate() {
        return loadX509FromPEMFile(mockServerCertificatePEMFile);
    }

    public X509Certificate mockServerCertificateAuthorityX509Certificate() {
        return loadX509FromPEMFile("org/mockserver/socket/CertificateAuthorityCertificate.pem");
    }

    /**
     * Load PrivateKey from PEM file.
     */
    private RSAPrivateKey loadPrivateKeyFromPEMFile(String filename) {
        try {
            String publicKeyFile = FileReader.readFileFromClassPathOrPath(filename);
            byte[] publicKeyBytes = DatatypeConverter.parseBase64Binary(publicKeyFile.replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", ""));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading private key from PEM file", e);
        }
    }

    /**
     * Load X509 from PEM file.
     */
    private X509Certificate loadX509FromPEMFile(String filename) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(FileReader.openStreamToFileFromClassPathOrPath(filename));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM file", e);
        }
    }

}
