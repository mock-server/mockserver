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
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileCreator;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
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

    /**
     * build or generate & save ca private key and certificate
     */
    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (dynamicallyUpdateCertificateAuthority() && certificateAuthorityCertificateNotYetCreated()) {
            try {
                AsymmetricKeyPairAlgorithm keyGenerationAndSigningAlgorithm = KeyAndCertificateFactory.DEFAULT_KEY_GENERATION_AND_SIGNING_ALGORITHM;
                KeyPair caKeyPair = AsymmetricKeyGenerator.createKeyPair(keyGenerationAndSigningAlgorithm);
                saveAsPEMFile(generateCACert(keyGenerationAndSigningAlgorithm, caKeyPair.getPublic(), caKeyPair.getPrivate()), certificateAuthorityX509CertificatePath(), "Certificate Authority X509 Certificate PEM");
                saveAsPEMFile(caKeyPair.getPrivate(), certificateAuthorityPrivateKeyPath(), "Certificate Authority Private Key PEM");
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

    /**
     * ca private key path
     */
    private String certificateAuthorityPrivateKeyPath() {
        if (dynamicallyUpdateCertificateAuthority()) {
            return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "PKCS8CertificateAuthorityPrivateKey.pem").getAbsolutePath();
        } else {
            return configuration.certificateAuthorityPrivateKey();
        }
    }

    /**
     * load ca private key
     */
    private RSAPrivateKey certificateAuthorityPrivateKey() {
        if (certificateAuthorityPrivateKey == null) {
            if (dynamicallyUpdateCertificateAuthority()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityPrivateKey = privateKeyFromPEMFile(certificateAuthorityPrivateKeyPath());
            if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded CA private key from path{}containing PEM{}")
                        .setArguments(FilePath.absolutePathFromClassPathOrPath(certificateAuthorityPrivateKeyPath()), certificateAuthorityPrivateKey)
                );
            } else if (MockServerLogger.isEnabled(INFO) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("loaded CA private key from path{}")
                        .setArguments(FilePath.absolutePathFromClassPathOrPath(certificateAuthorityPrivateKeyPath()))
                );
            }
        }
        return certificateAuthorityPrivateKey;
    }

    /**
     * ca certificate path
     */
    private String certificateAuthorityX509CertificatePath() {
        if (dynamicallyUpdateCertificateAuthority()) {
            return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "CertificateAuthorityCertificate.pem").getAbsolutePath();
        } else {
            return configuration.certificateAuthorityCertificate();
        }
    }

    /**
     * load ca certificate
     */
    public X509Certificate certificateAuthorityX509Certificate() {
        if (certificateAuthorityX509Certificate == null) {
            if (dynamicallyUpdateCertificateAuthority()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityX509Certificate = x509FromPEMFile(certificateAuthorityX509CertificatePath());
            if (MockServerLogger.isEnabled(DEBUG) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("loaded CA X509 from path{}containing PEM{}as{}")
                        .setArguments(FilePath.absolutePathFromClassPathOrPath(certificateAuthorityX509CertificatePath()), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()), certificateAuthorityX509Certificate)
                );
            } else if (MockServerLogger.isEnabled(INFO) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("loaded CA X509 from path{}containing PEM{}")
                        .setArguments(FilePath.absolutePathFromClassPathOrPath(certificateAuthorityX509CertificatePath()), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()))
                );
            }
        }
        return certificateAuthorityX509Certificate;
    }

    /**
     * generate ca certificate
     */
    private X509Certificate generateCACert(AsymmetricKeyPairAlgorithm keyGenerationAndSigningAlgorithm, PublicKey publicKey, PrivateKey privateKey) throws Exception {

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

        X509Certificate cert = signCertificate(keyGenerationAndSigningAlgorithm, builder, privateKey);
        cert.checkValidity(new Date());
        cert.verify(publicKey);

        return cert;
    }

    /**
     * build or generate & save leaf private key and certificate
     */
    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        if (customPrivateKeyAndCertificateProvided()) {
            try {
                if (dynamicallyUpdateCertificateAuthority()) {
                    buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
                }
                AsymmetricKeyPairAlgorithm keyGenerationAndSigningAlgorithm = KeyAndCertificateFactory.DEFAULT_KEY_GENERATION_AND_SIGNING_ALGORITHM;
                KeyPair keyPair = AsymmetricKeyGenerator.createKeyPair(keyGenerationAndSigningAlgorithm);
                privateKey = keyPair.getPrivate();
                x509Certificate = generateLeafCert(
                    keyGenerationAndSigningAlgorithm,
                    keyPair.getPublic(),
                    certificateAuthorityX509Certificate(),
                    certificateAuthorityPrivateKey(),
                    certificateAuthorityX509Certificate().getPublicKey(),
                    configuration.sslCertificateDomainName(),
                    configuration.sslSubjectAlternativeNameDomains(),
                    configuration.sslSubjectAlternativeNameIps()
                );
                if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("created new X509{}with SAN Domain Names{}and IPs{}")
                            .setArguments(x509Certificate(), configuration.sslSubjectAlternativeNameDomains(), configuration.sslSubjectAlternativeNameIps())
                    );
                }
                if (configuration.preventCertificateDynamicUpdate()) {
                    saveAsPEMFile(x509Certificate, x509CertificatePath(), "X509 Certificate PEM");
                    saveAsPEMFile(privateKey, privateKeyPath(), "Private Key PEM");
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
     * leaf private key path
     */
    private String privateKeyPath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "PKCS8PrivateKey.pem").getAbsolutePath();
    }

    /**
     * load leaf private key
     */
    public PrivateKey privateKey() {
        if (customPrivateKeyAndCertificateProvided()) {
            return privateKey;
        } else {
            return privateKeyFromPEMFile(configuration.privateKeyPath());
        }
    }

    /**
     * leaf certificate path
     */
    private String x509CertificatePath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "Certificate.pem").getAbsolutePath();
    }

    /**
     * load leaf certificate
     */
    public X509Certificate x509Certificate() {
        if (customPrivateKeyAndCertificateProvided()) {
            return x509Certificate;
        } else {
            return x509FromPEMFile(configuration.x509CertificatePath());
        }
    }

    /**
     * generate signed leaf certificate
     */
    private X509Certificate generateLeafCert(AsymmetricKeyPairAlgorithm keyGenerationAndSigningAlgorithm, PublicKey publicKey, X509Certificate certificateAuthorityCert, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, Set<String> subjectAlternativeNameDomains, Set<String> subjectAlternativeNameIps) throws Exception {

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
        X509Certificate signedX509Certificate = signCertificate(keyGenerationAndSigningAlgorithm, builder, certificateAuthorityPrivateKey);

        // validate
        signedX509Certificate.checkValidity(new Date());
        signedX509Certificate.verify(certificateAuthorityPublicKey);

        return signedX509Certificate;
    }

    /**
     * sign CA or leaf certificate
     */
    private X509Certificate signCertificate(AsymmetricKeyPairAlgorithm keyGenerationAndSigningAlgorithm, X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(keyGenerationAndSigningAlgorithm.getSigningAlgorithm()).setProvider(PROVIDER_NAME).build(privateKey);
        return new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
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

    private boolean customPrivateKeyAndCertificateProvided() {
        return isBlank(configuration.privateKeyPath()) || isBlank(configuration.x509CertificatePath());
    }

    private boolean dynamicallyUpdateCertificateAuthority() {
        return configuration.dynamicallyCreateCertificateAuthorityCertificate();
    }

    public boolean certificateAuthorityCertificateNotYetCreated() {
        return !validX509PEMFileExists(certificateAuthorityX509CertificatePath());
    }

    private void saveAsPEMFile(Object object, String absolutePath, String type) throws IOException {
        if (MockServerLogger.isEnabled(INFO) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setMessageFormat("created dynamic " + type + " file at{}")
                    .setArguments(absolutePath)
            );
        }
        try (FileWriter pemfileWriter = new FileWriter(FileCreator.createFileIfNotExists(type, new File(absolutePath)))) {
            try (JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(pemfileWriter)) {
                jcaPEMWriter.writeObject(object);
            }
        }
    }

    @Override
    public List<X509Certificate> certificateChain() {
        final List<X509Certificate> result = new ArrayList<>();
        result.add(x509Certificate());
        result.add(certificateAuthorityX509Certificate());
        return result;
    }
}
