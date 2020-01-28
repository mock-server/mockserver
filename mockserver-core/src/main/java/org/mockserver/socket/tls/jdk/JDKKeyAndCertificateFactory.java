package org.mockserver.socket.tls.jdk;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.mockserver.socket.tls.jdk.X509Generator.*;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class JDKKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private final MockServerLogger mockServerLogger;
    private final X509Generator x509Generator;

    private String mockCertificatePEMFile;
    private String mockPrivateKeyPEMFile;
    private String certificateAuthorityCertificatePEMFile;
    private String certificateAuthorityPrivateKeyPEMFile;

    public JDKKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.x509Generator = new X509Generator(new MockServerLogger());
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (isNotBlank(directoryToSaveDynamicSSLCertificate())
            && isBlank(certificateAuthorityCertificatePEMFile)) {
            certificateAuthorityCertificatePEMFile = new File(directoryToSaveDynamicSSLCertificate(), "CertificateAuthorityCertificate.pem").getAbsolutePath();
        }
        if (!new File(certificateAuthorityCertificatePEMFile).exists()) {
            try {
                X509AndPrivateKey x509AndPrivateKey = x509Generator.generateRootX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setKeyPairSize(ROOT_KEY_SIZE)
                );

                certificateAuthorityCertificatePEMFile = savePEMToFile(x509AndPrivateKey.getCert(), "CertificateAuthorityCertificate.pem", false, "X509 key");
                certificateAuthorityPrivateKeyPEMFile = savePEMToFile(x509AndPrivateKey.getPrivateKey(), "PKCS8CertificateAuthorityPrivateKey.pem", false, "private key");
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
    }

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        try {
            String caPrivateKey = certificateAuthorityPrivateKey();
            X509Certificate certificateAuthorityX509Certificate = certificateAuthorityX509Certificate();
            X509AndPrivateKey x509AndPrivateKey = x509Generator.generateLeafX509AndPrivateKey(
                new CertificateSigningRequest()
                    .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                    .setSigningAlgorithm(SIGNING_ALGORITHM)
                    .setCommonName(ROOT_COMMON_NAME)
                    .setCommonName(sslCertificateDomainName())
                    .addSubjectAlternativeNames(sslSubjectAlternativeNameDomains())
                    .addSubjectAlternativeNames(sslSubjectAlternativeNameIps())
                    .setKeyPairSize(MOCK_KEY_SIZE),
                certificateAuthorityX509Certificate.getIssuerDN().getName(),
                caPrivateKey
            );

            String randomUUID = UUID.randomUUID().toString();
            mockCertificatePEMFile = savePEMToFile(x509AndPrivateKey.getCert(), "MockServerCertificate" + randomUUID + ".pem", true, "X509 key");
            mockPrivateKeyPEMFile = savePEMToFile(x509AndPrivateKey.getPrivateKey(), "MockServerPrivateKey" + randomUUID + ".pem", true, "private key");
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

    @SuppressWarnings("DuplicatedCode")
    private String savePEMToFile(String pem, String filename, boolean deleteOnExit, String type) throws IOException {
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
        try (FileWriter fileWriter = new FileWriter(pemFile)) {
            fileWriter.write(pem);
        }
        if (deleteOnExit) {
            pemFile.deleteOnExit();
        }
        return pemFile.getAbsolutePath();
    }

    public PrivateKey privateKey() {
        RSAPrivateKey privateKey = privateKeyFromPEMFile(mockPrivateKeyPEMFile);
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(TRACE)
                    .setMessageFormat("loaded CA private key{}from PEM{}")
                    .setArguments(privateKey, FileReader.readFileFromClassPathOrPath(mockPrivateKeyPEMFile))
            );
        }
        return privateKey;
    }

    public X509Certificate x509Certificate() {
        X509Certificate x509Certificate = x509FromPEMFile(mockCertificatePEMFile);
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(TRACE)
                    .setMessageFormat("loaded X509{}from PEM{}")
                    .setArguments(x509Certificate, FileReader.readFileFromClassPathOrPath(mockCertificatePEMFile))
            );
        }
        return x509Certificate;
    }

    public boolean certificateNotYetCreated() {
        return !validX509PEMFileExists(mockCertificatePEMFile);
    }

    private String certificateAuthorityPrivateKey() {
        if (ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate()) {
            buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            String privateKey = FileReader.readFileFromClassPathOrPath(certificateAuthorityPrivateKeyPEMFile);
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded dynamic CA private key from PEM{}")
                        .setArguments(privateKey)
                );
            }
            return privateKey;
        } else {
            String privateKey = FileReader.readFileFromClassPathOrPath(ConfigurationProperties.certificateAuthorityPrivateKey());
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded CA private key from PEM{}")
                        .setArguments(privateKey)
                );
            }
            return privateKey;
        }
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        if (ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate()) {
            buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            X509Certificate x509Certificate = x509FromPEMFile(certificateAuthorityCertificatePEMFile);
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded dynamic CA X509{}from PEM{}")
                        .setArguments(x509Certificate, FileReader.readFileFromClassPathOrPath(certificateAuthorityCertificatePEMFile))
                );
            }
            return x509Certificate;
        } else {
            X509Certificate x509Certificate = x509FromPEMFile(certificateAuthorityCertificate());
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(TRACE)
                        .setMessageFormat("loaded CA X509{}from PEM{}")
                        .setArguments(x509Certificate, FileReader.readFileFromClassPathOrPath(certificateAuthorityCertificate()))
                );
            }
            return x509Certificate;
        }
    }
}
