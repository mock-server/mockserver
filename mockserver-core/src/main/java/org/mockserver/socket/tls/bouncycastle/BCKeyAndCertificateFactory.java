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
import org.mockserver.configuration.Configuration;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
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
import static org.mockserver.socket.tls.PEMToFile.*;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class BCKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private PrivateKey privateKey;
    private X509Certificate x509Certificate;
    private RSAPrivateKey certificateAuthorityPrivateKey;
    private X509Certificate certificateAuthorityX509Certificate;

    public BCKeyAndCertificateFactory(Configuration configuration, MockServerLogger mockServerLogger) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (dynamicallyUpdateCertificateAuthority() && certificateAuthorityCertificateNotYetCreated()) {
            try {
                KeyPair caKeyPair = generateKeyPair(KeyAndCertificateFactory.DEFAULT_ROOT_KEY_SIZE);

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
        if (MockServerLogger.isEnabled(INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
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
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("failed to create the file{}while attempting to save Certificate Authority " + type + " PEM file")
                            .setArguments(file.getAbsolutePath())
                    );

                }
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(ERROR)
                        .setMessageFormat("failed to create the file{}while attempting to save Certificate Authority " + type + " PEM file")
                        .setArguments(file.getAbsolutePath())
                        .setThrowable(throwable)
                );
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

    private boolean dynamicallyUpdateCertificateAuthority() {
        return configuration.dynamicallyCreateCertificateAuthorityCertificate();
    }

    public boolean certificateAuthorityCertificateNotYetCreated() {
        return !validX509PEMFileExists(certificateAuthorityX509CertificatePath());
    }

    private String certificateAuthorityPrivateKeyPath() {
        if (dynamicallyUpdateCertificateAuthority()) {
            return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "PKCS8CertificateAuthorityPrivateKey.pem").getAbsolutePath();
        } else {
            return configuration.certificateAuthorityPrivateKey();
        }
    }

    private String certificateAuthorityX509CertificatePath() {
        if (dynamicallyUpdateCertificateAuthority()) {
            return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "CertificateAuthorityCertificate.pem").getAbsolutePath();
        } else {
            return configuration.certificateAuthorityCertificate();
        }
    }

    private X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        // signers name
        X500Name issuerName = new X500Name("CN=" + ROOT_COMMON_NAME + ", O=" + ORGANISATION + ", L=" + LOCALITY + ", ST=" + STATE + ", C=" + COUNTRY);

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
            if (dynamicallyUpdateCertificateAuthority()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityPrivateKey = privateKeyFromPEMFile(certificateAuthorityPrivateKeyPath());
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded CA private key from path{}containing PEM{}")
                        .setArguments(FileReader.absolutePathFromClassPathOrPath(certificateAuthorityPrivateKeyPath()), certificateAuthorityPrivateKey)
                );
            } else if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("loaded CA private key from path{}")
                        .setArguments(FileReader.absolutePathFromClassPathOrPath(certificateAuthorityPrivateKeyPath()))
                );
            }
        }
        return certificateAuthorityPrivateKey;
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        if (certificateAuthorityX509Certificate == null) {
            if (dynamicallyUpdateCertificateAuthority()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityX509Certificate = x509FromPEMFile(certificateAuthorityX509CertificatePath());
            if (MockServerLogger.isEnabled(DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("loaded CA X509 from path{}containing PEM{}as{}")
                        .setArguments(FileReader.absolutePathFromClassPathOrPath(certificateAuthorityX509CertificatePath()), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()), certificateAuthorityX509Certificate)
                );
            } else if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("loaded CA X509 from path{}containing PEM{}")
                        .setArguments(FileReader.absolutePathFromClassPathOrPath(certificateAuthorityX509CertificatePath()), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()))
                );
            }
        }
        return certificateAuthorityX509Certificate;
    }

    private boolean customPrivateKeyAndCertificateProvided() {
        return isBlank(configuration.privateKeyPath()) || isBlank(configuration.x509CertificatePath());
    }

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        if (customPrivateKeyAndCertificateProvided()) {
            try {
                if (dynamicallyUpdateCertificateAuthority()) {
                    buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
                }
                KeyPair keyPair = generateKeyPair(KeyAndCertificateFactory.DEFAULT_LEAF_KEY_SIZE);
                privateKey = keyPair.getPrivate();
                x509Certificate = createCASignedCert(
                    keyPair.getPublic(),
                    certificateAuthorityX509Certificate(),
                    certificateAuthorityPrivateKey(),
                    certificateAuthorityX509Certificate().getPublicKey(),
                    configuration.sslCertificateDomainName(),
                    configuration.sslSubjectAlternativeNameDomains(),
                    configuration.sslSubjectAlternativeNameIps()
                );
                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("created new X509{}with SAN Domain Names{}and IPs{}")
                            .setArguments(x509Certificate(), configuration.sslSubjectAlternativeNameDomains(), configuration.sslSubjectAlternativeNameIps())
                    );
                }
                if (configuration.preventCertificateDynamicUpdate()) {
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
    private X509Certificate createCASignedCert(PublicKey publicKey, X509Certificate certificateAuthorityCert, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, Set<String> subjectAlternativeNameDomains, Set<String> subjectAlternativeNameIps) throws Exception {

        // signers name
        X500Name issuer = new X509CertificateHolder(certificateAuthorityCert.getEncoded()).getSubject();

        // subjects name - the same as we are self signed.
        X500Name subject = new X500Name("CN=" + domain + ", O=" + ORGANISATION + ", L=" + LOCALITY + ", ST=" + STATE + ", C=" + COUNTRY);

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
        ContentSigner signer = new JcaContentSignerBuilder(DEFAULT_SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(privateKey);
        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
    }

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    private KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(DEFAULT_KEY_GENERATION_ALGORITHM, PROVIDER_NAME);
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
        return customPrivateKeyAndCertificateProvided() && x509Certificate == null;
    }

    private String privateKeyPath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "PKCS8PrivateKey.pem").getAbsolutePath();
    }

    private String x509CertificatePath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "Certificate.pem").getAbsolutePath();
    }

    public PrivateKey privateKey() {
        if (customPrivateKeyAndCertificateProvided()) {
            return privateKey;
        } else {
            return privateKeyFromPEMFile(configuration.privateKeyPath());
        }
    }

    public X509Certificate x509Certificate() {
        if (customPrivateKeyAndCertificateProvided()) {
            return x509Certificate;
        } else {
            return x509FromPEMFile(configuration.x509CertificatePath());
        }
    }
}
