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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.mockserver.socket.tls.jdk.X509Generator.*;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class JDKKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private final MockServerLogger mockServerLogger;
    private final X509Generator x509Generator;

    private X509AndPrivateKey x509AndPrivateKey;

    public JDKKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.x509Generator = new X509Generator(new MockServerLogger());
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (dynamicCertificateAuthorityUpdate() && certificateAuthorityCertificateNotYetCreated()) {
            try {
                X509AndPrivateKey x509AndPrivateKey = x509Generator.generateRootX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setKeyPairSize(ROOT_KEY_SIZE)
                );

                saveCertificateAuthorityPEMToFile(x509AndPrivateKey.getCert(), certificateAuthorityX509CertificatePath(), "X509 Certificate");
                saveCertificateAuthorityPEMToFile(x509AndPrivateKey.getPrivateKey(), certificateAuthorityPrivateKeyPath(), "Private Key");
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

    private void saveCertificateAuthorityPEMToFile(String pem, String absolutePath, String type) throws IOException {
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(LogEntry.LogMessageType.DEBUG)
                .setLogLevel(DEBUG)
                .setMessageFormat("created dynamic Certificate Authority " + type + " PEM file at{}")
                .setArguments(absolutePath)
        );
        try (FileWriter fileWriter = new FileWriter(createFileIfNotExists(type, new File(absolutePath)))) {
            fileWriter.write(pem);
        }
    }

    private File createFileIfNotExists(String type, File file) {
        if (!file.exists()) {
            try {
                createParentDirs(file);
                if (!file.createNewFile()) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.WARN)
                            .setLogLevel(ERROR)
                            .setMessageFormat("failed to create the file{}while attempting to save Certificate Authority " + type + " PEM file")
                            .setArguments(file.getAbsolutePath())
                    );
                }
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
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

    private boolean dynamicCertificateAuthorityUpdate() {
        return ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate() && isNotBlank(ConfigurationProperties.directoryToSaveDynamicSSLCertificate());
    }

    public boolean certificateAuthorityCertificateNotYetCreated() {
        return !validX509PEMFileExists(certificateAuthorityX509CertificatePath());
    }

    private String certificateAuthorityX509CertificatePath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "CertificateAuthorityCertificate.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityCertificate();
        }
    }

    private String certificateAuthorityPrivateKeyPath() {
        if (dynamicCertificateAuthorityUpdate()) {
            return new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), "PKCS8CertificateAuthorityPrivateKey.pem").getAbsolutePath();
        } else {
            return ConfigurationProperties.certificateAuthorityPrivateKey();
        }
    }

    private String certificateAuthorityPrivateKey() {
        if (dynamicCertificateAuthorityUpdate()) {
            buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
        }
        String privateKey = FileReader.readFileFromClassPathOrPath(certificateAuthorityPrivateKeyPath());
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(TRACE)
                    .setMessageFormat("loaded dynamic CA private key from path{}PEM{}")
                    .setArguments(certificateAuthorityPrivateKeyPath(), privateKey)
            );
        }
        return privateKey;
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        if (dynamicCertificateAuthorityUpdate()) {
            buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
        }
        X509Certificate x509Certificate = x509FromPEMFile(certificateAuthorityX509CertificatePath());
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(TRACE)
                    .setMessageFormat("loaded dynamic CA X509{}from path{}from PEM{}")
                    .setArguments(x509Certificate, certificateAuthorityX509CertificatePath(), FileReader.readFileFromClassPathOrPath(certificateAuthorityX509CertificatePath()))
            );
        }
        return x509Certificate;
    }

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        try {
            if (dynamicCertificateAuthorityUpdate()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            String caPrivateKey = certificateAuthorityPrivateKey();
            X509Certificate certificateAuthorityX509Certificate = certificateAuthorityX509Certificate();
            x509AndPrivateKey = x509Generator.generateLeafX509AndPrivateKey(
                new CertificateSigningRequest()
                    .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                    .setSigningAlgorithm(SIGNING_ALGORITHM)
                    .setCommonName(ROOT_COMMON_NAME)
                    .setCommonName(ConfigurationProperties.sslCertificateDomainName())
                    .addSubjectAlternativeNames(ConfigurationProperties.sslSubjectAlternativeNameDomains())
                    .addSubjectAlternativeNames(ConfigurationProperties.sslSubjectAlternativeNameIps())
                    .setKeyPairSize(MOCK_KEY_SIZE),
                certificateAuthorityX509Certificate.getIssuerDN().getName(),
                caPrivateKey
            );
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

    private String savePEMToFile(String pem, String filename, String type) throws IOException {
        File pemFile;
        if (isNotBlank(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()) && new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()).exists()) {
            pemFile = new File(new File(ConfigurationProperties.directoryToSaveDynamicSSLCertificate()), filename);
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
        pemFile.deleteOnExit();
        return pemFile.getAbsolutePath();
    }

    public boolean certificateNotYetCreated() {
        return x509AndPrivateKey == null;
    }

    public PrivateKey privateKey() {
        return privateKeyFromPEM(x509AndPrivateKey.getPrivateKey());
    }

    public X509Certificate x509Certificate() {
        return x509FromPEM(x509AndPrivateKey.getCert());
    }
}
