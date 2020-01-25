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
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.mockserver.socket.tls.jdk.X509Generator.*;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class JDKKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private final MockServerLogger mockServerLogger;
    private final X509Generator x509Generator;

    private String mockServerCertificatePEMFile;
    private String mockServerPrivateKeyPEMFile;

    public JDKKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.x509Generator = new X509Generator(new MockServerLogger());
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        try {
            X509AndPrivateKey x509AndPrivateKey = x509Generator.generateRootX509AndPrivateKey(
                new CertificateSigningRequest()
                    .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                    .setSigningAlgorithm(SIGNING_ALGORITHM)
                    .setCommonName(ROOT_COMMON_NAME)
                    .setKeyPairSize(ROOT_KEY_SIZE)
            );

            savePEMToFile(x509AndPrivateKey.getCert(), "CertificateAuthorityCertificate.pem", false, "X509 key");
            savePEMToFile(x509AndPrivateKey.getPrivateKey(), "PKCS8CertificateAuthorityPrivateKey.pem", false, "private key");
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

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        try {
            String caPrivateKey = FileReader.readFileFromClassPathOrPath(ConfigurationProperties.certificateAuthorityPrivateKey());
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
            mockServerCertificatePEMFile = savePEMToFile(x509AndPrivateKey.getCert(), "MockServerCertificate" + randomUUID + ".pem", true, "X509 key");
            mockServerPrivateKeyPEMFile = savePEMToFile(x509AndPrivateKey.getPrivateKey(), "MockServerPrivateKey" + randomUUID + ".pem", true, "private key");
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
        return privateKeyFromPEMFile(mockServerPrivateKeyPEMFile);
    }

    public X509Certificate x509Certificate() {
        return x509FromPEMFile(mockServerCertificatePEMFile);
    }

    public boolean certificateNotYetCreated() {
        return !validX509PEMFileExists(mockServerCertificatePEMFile);
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        return x509FromPEMFile(ConfigurationProperties.certificateAuthorityCertificate());
    }
}
