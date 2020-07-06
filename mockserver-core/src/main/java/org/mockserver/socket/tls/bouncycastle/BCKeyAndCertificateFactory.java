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
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.slf4j.event.Level;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.preventCertificateDynamicUpdate;
import static org.mockserver.socket.tls.PEMToFile.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.NOT_AFTER;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.NOT_BEFORE;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class BCKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";

    private final MockServerLogger mockServerLogger;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private PrivateKey privateKey;
    private X509Certificate x509Certificate;
    private RSAPrivateKey certificateAuthorityPrivateKey;
    private X509Certificate certificateAuthorityX509Certificate;

    public BCKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (dynamicCertificateAuthorityUpdate() && certificateAuthorityCertificateNotYetCreated()) {
            try {
                KeyPair caKeyPair = generateKeyPair(CertificateSigningRequest.ROOT_KEY_SIZE);

                saveAsPEMFile(createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate()), certificateAuthorityX509CertificatePath(), "Certificate Authority X509 Certificate");
                saveAsPEMFile(caKeyPair.getPrivate(), certificateAuthorityPrivateKeyPath(), "Certificate Authority Private Key");
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while generating certificate authority private key and X509 certificate")
                        .setThrowable(e)
                );
            }
        }
    }

    private void saveAsPEMFile(Object object, String absolutePath, String type) throws IOException {
        if (MockServerLogger.isEnabled(DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat("created dynamic " + type + " PEM file at{}")
                    .setArguments(absolutePath)
            );
        }
        try (FileWriter pemfileWriter = new FileWriter(createFileIfNotExists(type, new File(absolutePath)))) {
            try (JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(pemfileWriter)) {
                jcaPEMWriter.writeObject(object);
            }
        }
    }

    private File createFileIfNotExists(String type, File file) {
        if (!file.exists()) {
            try {
                createParentDirs(file);
                if (!file.createNewFile()) {
                    if (MockServerLogger.isEnabled(ERROR)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(ERROR)
                                .setMessageFormat("failed to create the file{}while attempting to save Certificate Authority " + type + " PEM file")
                                .setArguments(file.getAbsolutePath())
                        );
                    }
                }
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(ERROR)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("failed to create the file{}while attempting to save Certificate Authority " + type + " PEM file")
                            .setArguments(file.getAbsolutePath())
                            .setThrowable(throwable)
                    );
                }
            }
        }
        return file;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void createParentDirs(File file) throws IOException {
        File parent = file.getCanonicalFile().getParentFile();
        if (parent == null) {
            /*
             * The given directory is a filesystem root. All zero of its ancestors exist. This doesn't
             * mean that the root itself exists -- consider x:\ on a Windows machine without such a drive
             * -- or even that the caller can create it, but this method makes no such guarantees even for
             * non-root files.
             */
            return;
        }
        createParentDirs(parent);
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!parent.isDirectory()) {
            throw new IOException("Unable to create parent directories of " + file);
        }
    }

    private boolean dynamicCertificateAuthorityUpdate() {
        return ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate() && isNotBlank(ConfigurationProperties.directoryToSaveDynamicSSLCertificate());
    }

    public boolean certificateAuthorityCertificateNotYetCreated() {
        return !validX509PEMFileExists(certificateAuthorityX509CertificatePath());
    }

    private String certificateAuthorityPrivateKeyPath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "PKCS8CertificateAuthorityPrivateKey.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityPrivateKey();
        }
    }

    private String certificateAuthorityX509CertificatePath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "CertificateAuthorityCertificate.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityCertificate();
        }
    }

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

    private RSAPrivateKey certificateAuthorityPrivateKey() {
        if (certificateAuthorityPrivateKey == null) {
            if (dynamicCertificateAuthorityUpdate()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityPrivateKey = privateKeyFromPEMFile(certificateAuthorityPrivateKeyPath());
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded dynamic CA private key from path{}PEM{}")
                        .setArguments(certificateAuthorityPrivateKeyPath(), certificateAuthorityPrivateKey)
                );
            }
        }
        return certificateAuthorityPrivateKey;
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        if (certificateAuthorityX509Certificate == null) {
            if (dynamicCertificateAuthorityUpdate()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityX509Certificate = x509FromPEMFile(certificateAuthorityX509CertificatePath());
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded dynamic CA X509 from path{}from PEM{}as{}")
                        .setArguments(certificateAuthorityX509CertificatePath(), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()), certificateAuthorityX509Certificate)
                );
            }
        }
        return certificateAuthorityX509Certificate;
    }

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        if (isBlank(ConfigurationProperties.privateKeyPath()) || isBlank(ConfigurationProperties.x509CertificatePath())) {
            try {
                if (dynamicCertificateAuthorityUpdate()) {
                    buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
                }
                KeyPair keyPair = generateKeyPair(CertificateSigningRequest.MOCK_KEY_SIZE);
                privateKey = keyPair.getPrivate();
                x509Certificate = createCASignedCert(
                    keyPair.getPublic(),
                    certificateAuthorityX509Certificate(),
                    certificateAuthorityPrivateKey(),
                    certificateAuthorityX509Certificate().getPublicKey(),
                    ConfigurationProperties.sslCertificateDomainName(),
                    ConfigurationProperties.sslSubjectAlternativeNameDomains(),
                    ConfigurationProperties.sslSubjectAlternativeNameIps()
                );
                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("created new X509{}with SAN Domain Names{}and IPs{}")
                            .setArguments(x509Certificate(), Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameDomains()), Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameIps()))
                    );
                }
                if (preventCertificateDynamicUpdate()) {
                    saveAsPEMFile(x509Certificate, x509CertificatePath(), "X509 Certificate");
                    saveAsPEMFile(privateKey, privateKeyPath(), "Private Key");
                }
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while generating private key and X509 certificate")
                        .setThrowable(e)
                );
            }
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
        X509Certificate signedX509Certificate = signCertificate(builder, certificateAuthorityPrivateKey);

        // validate
        signedX509Certificate.checkValidity(new Date());
        signedX509Certificate.verify(certificateAuthorityPublicKey);

        return signedX509Certificate;
    }

    private X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(privateKey);
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

    public boolean certificateNotYetCreated() {
        return x509Certificate == null;
    }

    private String privateKeyPath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "PKCS8PrivateKey.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityPrivateKey();
        }
    }

    private String x509CertificatePath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "Certificate.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityCertificate();
        }
    }

    public PrivateKey privateKey() {
        if (isNotBlank(ConfigurationProperties.privateKeyPath()) && isNotBlank(ConfigurationProperties.x509CertificatePath())) {
            return privateKeyFromPEMFile(ConfigurationProperties.privateKeyPath());
        } else {
            return privateKey;
        }
    }

    public X509Certificate x509Certificate() {
        if (isNotBlank(ConfigurationProperties.privateKeyPath()) && isNotBlank(ConfigurationProperties.x509CertificatePath())) {
            return x509FromPEMFile(ConfigurationProperties.x509CertificatePath());
        } else {
            return x509Certificate;
        }
    }
}
