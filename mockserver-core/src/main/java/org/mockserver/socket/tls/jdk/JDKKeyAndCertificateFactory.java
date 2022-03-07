package org.mockserver.socket.tls.jdk;

import org.mockserver.configuration.Configuration;
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
import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.socket.tls.PEMToFile.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class JDKKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final X509Generator x509Generator;

    private X509AndPrivateKey x509AndPrivateKey;
    private String certificateAuthorityPrivateKey;
    private X509Certificate certificateAuthorityX509Certificate;

    public JDKKeyAndCertificateFactory(Configuration configuration, MockServerLogger mockServerLogger) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.x509Generator = new X509Generator(new MockServerLogger());
    }

    @Override
    public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
        if (dynamicallyUpdateCertificateAuthority() && certificateAuthorityCertificateNotYetCreated()) {
            try {
                X509AndPrivateKey certificateAuthorityX509AndPrivateKey = x509Generator.generateRootX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setKeyPairSize(ROOT_KEY_SIZE)
                );

                saveAsPEMFile(certificateAuthorityX509AndPrivateKey.getCert(), certificateAuthorityX509CertificatePath(), "Certificate Authority X509 Certificate");
                saveAsPEMFile(certificateAuthorityX509AndPrivateKey.getPrivateKey(), certificateAuthorityPrivateKeyPath(), "Certificate Authority Private Key");
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

    private void saveAsPEMFile(String pem, String absolutePath, String type) throws IOException {
        if (MockServerLogger.isEnabled(DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat("created dynamic " + type + " PEM file at{}")
                    .setArguments(absolutePath)
            );
        }
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

    private String certificateAuthorityPrivateKey() {
        if (certificateAuthorityPrivateKey == null) {
            if (dynamicallyUpdateCertificateAuthority()) {
                buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
            }
            certificateAuthorityPrivateKey = FileReader.readFileFromClassPathOrPath(certificateAuthorityPrivateKeyPath());
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
                String caPrivateKey = certificateAuthorityPrivateKey();
                X509Certificate certificateAuthorityX509Certificate = certificateAuthorityX509Certificate();
                x509AndPrivateKey = x509Generator.generateLeafX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setCommonName(configuration.sslCertificateDomainName())
                        .addSubjectAlternativeNames(configuration.sslSubjectAlternativeNameDomains())
                        .addSubjectAlternativeNames(configuration.sslSubjectAlternativeNameIps())
                        .setKeyPairSize(MOCK_KEY_SIZE),
                    certificateAuthorityX509Certificate.getIssuerDN().getName(),
                    caPrivateKey,
                    certificateAuthorityX509Certificate
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
                    saveAsPEMFile(x509AndPrivateKey.getCert(), x509CertificatePath(), "X509 Certificate");
                    saveAsPEMFile(x509AndPrivateKey.getPrivateKey(), privateKeyPath(), "Private Key");
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

    public boolean certificateNotYetCreated() {
        return customPrivateKeyAndCertificateProvided() && x509AndPrivateKey == null;
    }

    private String privateKeyPath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "PKCS8PrivateKey.pem").getAbsolutePath();
    }

    private String x509CertificatePath() {
        return new File(new File(configuration.directoryToSaveDynamicSSLCertificate()), "Certificate.pem").getAbsolutePath();
    }

    public PrivateKey privateKey() {
        if (customPrivateKeyAndCertificateProvided()) {
            return privateKeyFromPEM(x509AndPrivateKey.getPrivateKey());
        } else {
            return privateKeyFromPEMFile(configuration.privateKeyPath());
        }
    }

    public X509Certificate x509Certificate() {
        if (customPrivateKeyAndCertificateProvided()) {
            return x509FromPEM(x509AndPrivateKey.getCert());
        } else {
            return x509FromPEMFile(configuration.x509CertificatePath());
        }
    }

}