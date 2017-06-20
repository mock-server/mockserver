package org.mockserver.socket;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
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
public class NettySslContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(SSLFactory.class);

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String CERTIFICATE_DOMAIN = "localhost";
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
     * <p>
     * Hundred years in the future from starting the proxy should be enough.
     */
    private static final Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);

    private static SslContext clientSslContext = null;
    private static SslContext serverSslContext = null;

    public synchronized SslContext createClientSslContext() {
        if (clientSslContext == null) {
            try {
                clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            } catch (SSLException e) {
                throw new RuntimeException("Exception creating SSL context for client", e);
            }
        }
        return clientSslContext;
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                serverSslContext = buildSslContext();
            } catch (Exception e) {
                throw new RuntimeException("Exception creating SSL context for client", e);
            }
        }
        return serverSslContext;
    }

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    private static KeyPair generateKeyPair(int keySize) throws Exception {
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
    private X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

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
        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));

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
    private X509Certificate createMockServerCert(PublicKey publicKey, X509Certificate certificateAuthorityCert, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

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
        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));

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
    SslContext buildSslContext() throws Exception {
        char[] keyStorePassword = ConfigurationProperties.javaKeyStorePassword().toCharArray();
        String domain = ConfigurationProperties.sslCertificateDomainName();
        String[] subjectAlternativeNameDomains = ConfigurationProperties.sslSubjectAlternativeNameDomains();
        String[] subjectAlternativeNameIps = ConfigurationProperties.sslSubjectAlternativeNameIps();


        //
        // personal keys
        //
        KeyPair keyPair = generateKeyPair(FAKE_KEYSIZE);
        PrivateKey mockServerPrivateKey = keyPair.getPrivate();
        PublicKey mockServerPublicKey = keyPair.getPublic();

        //
        // ca keys
        //
        PrivateKey caPrivateKey = loadPrivateKeyFromPEMFile("org/mockserver/socket/CertificateAuthorityPrivateKey.pem");
        X509Certificate caCert = loadX509FromPEMFile("org/mockserver/socket/CertificateAuthorityCertificate.pem");
//        X509Certificate caCert = (X509Certificate) new KeyStoreFactory().loadCertificateFromKeyStore("org/mockserver/socket/CertificateAuthorityKeyStore.jks", KEY_STORE_CA_ALIAS, keyStorePassword);

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
            saveCertificateAsPEMFile(caCert, "CertificateAuthorityCertificate.pem", false);
            saveCertificateAsPEMFile(caPublicKey, "CertificateAuthorityPublicKey.pem", false);
            saveCertificateAsPEMFile(caPrivateKey, "CertificateAuthorityPrivateKey.pem", false);
        }

        //
        // generate mockServer certificate
        //
        X509Certificate mockServerCert = createMockServerCert(mockServerPublicKey, caCert, caPrivateKey, caCert.getPublicKey(), domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);
        saveCertificateAsPEMFile(mockServerCert, "MockServerCertificate.pem", true);
        saveCertificateAsPEMFile(mockServerPublicKey, "MockServerPublicKey.pem", true);
        saveCertificateAsPEMFile(mockServerPrivateKey, "MockServerPrivateKey.pem", true);

        return SslContextBuilder.forServer(mockServerPrivateKey, new String(keyStorePassword), new X509Certificate[]{mockServerCert, caCert}).build();
    }

    /**
     * Saves X509Certificate as Base-64 encoded PEM file.
     */
    private void saveCertificateAsPEMFile(Object x509Certificate, String filename, boolean deleteOnExit) throws IOException {
        File pemFile = new File(filename);
        FileWriter pemfileWriter = new FileWriter(pemFile);
        JcaPEMWriter jcaPEMWriter = null;
        try {
            jcaPEMWriter = new JcaPEMWriter(pemfileWriter);
            jcaPEMWriter.writeObject(x509Certificate);
        } finally {
            IOUtils.closeQuietly(jcaPEMWriter);
            IOUtils.closeQuietly(pemfileWriter);
        }
        if (deleteOnExit) {
            pemFile.deleteOnExit();
        }
    }

    /**
     * Load PrivateKey from PEM file.
     */
    private RSAPrivateKey loadPrivateKeyFromPEMFile(String privateKeyFileName) {
        try {
            String publicKeyFile = IOUtils.toString(new InputStreamReader(NettySslContextFactory.class.getClassLoader().getResourceAsStream(privateKeyFileName)));
            byte[] publicKeyBytes = DatatypeConverter.parseBase64Binary(publicKeyFile.replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", ""));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            throw new RuntimeException("Exception reading private key from PEM file", e);
        }
    }

    /**
     * Load X509 from PEM file.
     */
    private X509Certificate loadX509FromPEMFile(String privateKeyFileName) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(
                            NettySslContextFactory.class.getClassLoader().getResourceAsStream(privateKeyFileName)
                    );
        } catch (Exception e) {
            throw new RuntimeException("Exception reading X509 from PEM file", e);
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
