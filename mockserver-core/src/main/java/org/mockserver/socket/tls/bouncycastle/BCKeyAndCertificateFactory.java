package org.mockserver.socket.tls.bouncycastle;

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
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.directoryToSaveDynamicSSLCertificate;
import static org.mockserver.socket.tls.jdk.X509Generator.*;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom, ganskef
 */
public class BCKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private final MockServerLogger mockServerLogger;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static String mockServerCertificatePEMFile;
    private static String mockServerPrivateKeyPEMFile;

    public BCKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    /**
     * Regenerate Certificate Authority public/private keys and X.509 certificate
     * <p>
     * Note: X.509 certificate should be stable so this method should rarely be used
     */
    public synchronized void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        try {
            KeyPair caKeyPair = generateKeyPair(ROOT_KEYSIZE);

            saveAsPEMFile(createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate()), "CertificateAuthorityCertificate.pem", false, "X509 key");
            saveAsPEMFile(caKeyPair.getPrivate(), "PKCS#1CertificateAuthorityPrivateKey.pem", false, "private key");
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while generating certificate authority private key and X509 certificate")
                    .setThrowable(e)
            );
        }
    }

    /**
     * Create a certificate to use by a Certificate Authority, signed by a self signed certificate.
     */
    private X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        // signers name
        X500Name issuerName = new X500Name("CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK");

        // serial
        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE));

        // create the certificate - version 3 (with subjects name same as issues as self signed)
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial, NOT_BEFORE, NOT_AFTER, issuerName, publicKey);
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
     * Create a KeyStore with a server certificate for the given domain and subject alternative names.
     */
    public synchronized void buildAndSavePrivateKeyAndX509Certificate() {
        try {
            // personal keys
            KeyPair keyPair = generateKeyPair(MOCK_KEYSIZE);
            PrivateKey mockServerPrivateKey = keyPair.getPrivate();
            PublicKey mockServerPublicKey = keyPair.getPublic();

            // ca keys
            PrivateKey caPrivateKey;
            try {
                caPrivateKey = privateKeyFromPEMFile(ConfigurationProperties.certificateAuthorityPrivateKey());
            } catch (Throwable throwable) {
                caPrivateKey = privateKeyFromPEMFile(ConfigurationProperties.certificateAuthorityPrivateKey().replaceAll("PKCS#8", "PKCS#1"));
            }
            X509Certificate caCert = certificateAuthorityX509Certificate();

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
            mockServerCertificatePEMFile = saveAsPEMFile(mockServerCert, "MockServerCertificate" + randomUUID + ".pem", true, "X509 key");
            mockServerPrivateKeyPEMFile = saveAsPEMFile(mockServerPrivateKey, "MockServerPrivateKey" + randomUUID + ".pem", true, "private key");
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while generating private key and X509 certificate")
                    .setThrowable(e)
            );
        }
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
        List<ASN1Encodable> subjectAlternativeNames = new ArrayList<>();
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
            DERSequence subjectAlternativeNamesExtension = new DERSequence(subjectAlternativeNames.toArray(new ASN1Encodable[0]));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        X509Certificate cert = signCertificate(builder, certificateAuthorityPrivateKey);

        cert.checkValidity(new Date());
        cert.verify(certificateAuthorityPublicKey);

        return cert;
    }

    private X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey signedWithPrivateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(signedWithPrivateKey);
        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
    }

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    private KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM, PROVIDER_NAME);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    private SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        try (ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        }
    }

    /**
     * Saves object as Base-64 encoded PEM file.
     */
    private String saveAsPEMFile(Object object, String filename, boolean deleteOnExit, String type) throws IOException {
        File pemFile;
        if (isNotBlank(directoryToSaveDynamicSSLCertificate()) && new File(directoryToSaveDynamicSSLCertificate()).exists()) {
            pemFile = new File(new File(directoryToSaveDynamicSSLCertificate()), filename);
            if (pemFile.exists()) {
                boolean deletedFile = pemFile.delete();
                if (!deletedFile) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.WARN)
                            .setLogLevel(WARN)
                            .setMessageFormat("failed to delete dynamic TLS certificate " + type + "  prior to creating new version for PEM file at{}")
                            .setArguments(pemFile.getAbsolutePath())
                    );
                }
            }
            boolean createFile = pemFile.createNewFile();
            if (!createFile) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
                        .setLogLevel(WARN)
                        .setMessageFormat("failed to created dynamic TLS certificate " + type + " PEM file at{}")
                        .setArguments(pemFile.getAbsolutePath())
                );
            }
        } else {
            pemFile = File.createTempFile(filename, null);
        }
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(LogEntry.LogMessageType.DEBUG)
                .setLogLevel(DEBUG)
                .setMessageFormat("created dynamic TLS certificate " + type + " PEM file at{}")
                .setArguments(pemFile.getAbsolutePath())
        );
        try (FileWriter pemfileWriter = new FileWriter(pemFile)) {
            try (JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(pemfileWriter)) {
                jcaPEMWriter.writeObject(object);
            }
        }
        if (deleteOnExit) {
            pemFile.deleteOnExit();
        }
        return pemFile.getAbsolutePath();
    }

    public PrivateKey privateKey() {
        return privateKeyFromPEMFile(mockServerPrivateKeyPEMFile);
    }

    public X509Certificate x509Certificate() {
        return x509FromPEMFile(mockServerCertificatePEMFile);
    }

    public boolean certificateCreated() {
        return validX509PEMFileExists(mockServerCertificatePEMFile);
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        return x509FromPEMFile(ConfigurationProperties.certificateAuthorityCertificate());
    }

}
