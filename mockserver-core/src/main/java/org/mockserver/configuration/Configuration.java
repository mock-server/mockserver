package org.mockserver.configuration;

import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class Configuration {

    // logging
    private Level logLevel;
    private Boolean disableSystemOut;
    private Boolean disableLogging;
    private Boolean detailedMatchFailures;
    private Boolean launchUIForLogLevelDebug;
    private Boolean metricsEnabled;

    // memory usage
    private Integer maxExpectations;
    private Integer defaultMaxExpectations;
    private Integer maxLogEntries;
    private Integer defaultMaxLogEntries;
    private Integer maxWebSocketExpectations;
    private Boolean outputMemoryUsageCsv;
    private String memoryUsageCsvDirectory;

    // scalability
    private Integer nioEventLoopThreadCount;
    private Integer actionHandlerThreadCount;
    private Integer webSocketClientEventLoopThreadCount;
    private Integer clientNioEventLoopThreadCount;
    private Long maxFutureTimeoutInMillis;
    private Boolean matchersFailFast;

    // socket
    private Long maxSocketTimeoutInMillis;
    private Integer socketConnectionTimeoutInMillis;
    private Boolean alwaysCloseSocketConnections;
    private String localBoundIP;

    // http request parsing
    private Integer maxInitialLineLength;
    private Integer maxHeaderSize;
    private Integer maxChunkSize;
    private Boolean useSemicolonAsQueryParameterSeparator;

    // CORS
    private Boolean enableCORSForAPI;
    private Boolean enableCORSForAllResponses;
    private String corsAllowHeaders;
    private String corsAllowMethods;
    private Boolean corsAllowCredentials;
    private Integer corsMaxAgeInSeconds;

    // mock initialization
    private String initializationClass;
    private String initializationJsonPath;
    private Boolean watchInitializationJson;

    // mock persistence
    private Boolean persistExpectations;
    private String persistedExpectationsPath;

    // proxy
    private Boolean attemptToProxyIfNoMatchingExpectation;
    private InetSocketAddress forwardHttpProxy;
    private InetSocketAddress forwardHttpsProxy;
    private InetSocketAddress forwardSocksProxy;
    private String forwardProxyAuthenticationUsername;
    private String forwardProxyAuthenticationPassword;
    private String proxyAuthenticationRealm;
    private String proxyAuthenticationUsername;
    private String proxyAuthenticationPassword;

    // liveness
    private String livenessHttpGetPath;

    // control plane authentication (TODO(jamesdbloom) missing from html)
    private Boolean controlPlaneTLSMutualAuthenticationRequired;
    private String controlPlaneTLSMutualAuthenticationCAChain;
    private String controlPlanePrivateKeyPath;
    private String controlPlaneX509CertificatePath;
    private Boolean controlPlaneJWTAuthenticationRequired;
    private String controlPlaneJWTAuthenticationJWKSource;

    // TLS
    private Boolean useBouncyCastleForKeyAndCertificateGeneration;
    // missing from html
    private Boolean proactivelyInitialiseTLS;

    // inbound - dynamic CA
    private Boolean dynamicallyCreateCertificateAuthorityCertificate;
    private String directoryToSaveDynamicSSLCertificate;

    // inbound - dynamic private key & x509
    private Boolean preventCertificateDynamicUpdate;
    private String sslCertificateDomainName;
    private String[] sslSubjectAlternativeNameDomains;
    private String[] sslSubjectAlternativeNameIps;

    // inbound - fixed CA
    private String certificateAuthorityPrivateKey;
    private String certificateAuthorityCertificate;

    // inbound - fixed private key & x509
    private String privateKeyPath;
    private String x509CertificatePath;

    // inbound - mTLS
    private Boolean tlsMutualAuthenticationRequired;
    private String tlsMutualAuthenticationCertificateChain;

    // outbound - CA
    private ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType;

    // outbound - fixed CA
    private String forwardProxyTLSCustomTrustX509Certificates;
    // outbound - fixed private key & x509
    private String forwardProxyPrivateKey;
    private String forwardProxyCertificateChain;

    public Integer defaultMaxExpectations() {
        if (defaultMaxExpectations == null) {
            defaultMaxExpectations = ConfigurationProperties.defaultMaxExpectations();
        }
        return defaultMaxExpectations;
    }

    public Configuration defaultMaxExpectations(Integer defaultMaxExpectations) {
        this.defaultMaxExpectations = defaultMaxExpectations;
        return this;
    }

    public Integer maxExpectations() {
        if (maxExpectations == null) {
            maxExpectations = ConfigurationProperties.maxExpectations();
        }
        return maxExpectations;
    }

    public Configuration maxExpectations(Integer maxExpectations) {
        this.maxExpectations = maxExpectations;
        return this;
    }

    public Integer defaultMaxLogEntries() {
        if (defaultMaxLogEntries == null) {
            defaultMaxLogEntries = ConfigurationProperties.defaultMaxLogEntries();
        }
        return defaultMaxLogEntries;
    }

    public Configuration defaultMaxLogEntries(Integer defaultMaxLogEntries) {
        this.defaultMaxLogEntries = defaultMaxLogEntries;
        return this;
    }

    public Integer maxLogEntries() {
        if (maxLogEntries == null) {
            maxLogEntries = ConfigurationProperties.maxLogEntries();
        }
        return maxLogEntries;
    }

    public Configuration maxLogEntries(Integer maxLogEntries) {
        this.maxLogEntries = maxLogEntries;
        return this;
    }

    public Boolean outputMemoryUsageCsv() {
        if (outputMemoryUsageCsv == null) {
            outputMemoryUsageCsv = ConfigurationProperties.outputMemoryUsageCsv();
        }
        return outputMemoryUsageCsv;
    }

    public Configuration outputMemoryUsageCsv(Boolean outputMemoryUsageCsv) {
        this.outputMemoryUsageCsv = outputMemoryUsageCsv;
        return this;
    }

    public String memoryUsageCsvDirectory() {
        if (memoryUsageCsvDirectory == null) {
            memoryUsageCsvDirectory = ConfigurationProperties.memoryUsageCsvDirectory();
        }
        return memoryUsageCsvDirectory;
    }

    public Configuration memoryUsageCsvDirectory(String memoryUsageCsvDirectory) {
        this.memoryUsageCsvDirectory = memoryUsageCsvDirectory;
        return this;
    }

    public Integer maxWebSocketExpectations() {
        if (maxWebSocketExpectations == null) {
            maxWebSocketExpectations = ConfigurationProperties.maxWebSocketExpectations();
        }
        return maxWebSocketExpectations;
    }

    public Configuration maxWebSocketExpectations(Integer maxWebSocketExpectations) {
        this.maxWebSocketExpectations = maxWebSocketExpectations;
        return this;
    }

    public Integer maxInitialLineLength() {
        if (maxInitialLineLength == null) {
            maxInitialLineLength = ConfigurationProperties.maxInitialLineLength();
        }
        return maxInitialLineLength;
    }

    public Configuration maxInitialLineLength(Integer maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public Integer maxHeaderSize() {
        if (maxHeaderSize == null) {
            maxHeaderSize = ConfigurationProperties.maxHeaderSize();
        }
        return maxHeaderSize;
    }

    public Configuration maxHeaderSize(Integer maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
        return this;
    }

    public Integer maxChunkSize() {
        if (maxChunkSize == null) {
            maxChunkSize = ConfigurationProperties.maxChunkSize();
        }
        return maxChunkSize;
    }

    public Configuration maxChunkSize(Integer maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public Integer nioEventLoopThreadCount() {
        if (nioEventLoopThreadCount == null) {
            nioEventLoopThreadCount = ConfigurationProperties.nioEventLoopThreadCount();
        }
        return nioEventLoopThreadCount;
    }

    public Configuration nioEventLoopThreadCount(Integer nioEventLoopThreadCount) {
        this.nioEventLoopThreadCount = nioEventLoopThreadCount;
        return this;
    }

    public Integer clientNioEventLoopThreadCount() {
        if (clientNioEventLoopThreadCount == null) {
            clientNioEventLoopThreadCount = ConfigurationProperties.clientNioEventLoopThreadCount();
        }
        return clientNioEventLoopThreadCount;
    }

    public Configuration clientNioEventLoopThreadCount(Integer clientNioEventLoopThreadCount) {
        this.clientNioEventLoopThreadCount = clientNioEventLoopThreadCount;
        return this;
    }

    public Integer actionHandlerThreadCount() {
        if (actionHandlerThreadCount == null) {
            actionHandlerThreadCount = ConfigurationProperties.actionHandlerThreadCount();
        }
        return actionHandlerThreadCount;
    }

    public Configuration actionHandlerThreadCount(Integer actionHandlerThreadCount) {
        this.actionHandlerThreadCount = actionHandlerThreadCount;
        return this;
    }

    public Integer webSocketClientEventLoopThreadCount() {
        if (webSocketClientEventLoopThreadCount == null) {
            webSocketClientEventLoopThreadCount = ConfigurationProperties.webSocketClientEventLoopThreadCount();
        }
        return webSocketClientEventLoopThreadCount;
    }

    public Configuration webSocketClientEventLoopThreadCount(Integer webSocketClientEventLoopThreadCount) {
        this.webSocketClientEventLoopThreadCount = webSocketClientEventLoopThreadCount;
        return this;
    }

    public Long maxSocketTimeoutInMillis() {
        if (maxSocketTimeoutInMillis == null) {
            maxSocketTimeoutInMillis = ConfigurationProperties.maxSocketTimeout();
        }
        return maxSocketTimeoutInMillis;
    }

    public Configuration maxSocketTimeoutInMillis(Long maxSocketTimeoutInMillis) {
        this.maxSocketTimeoutInMillis = maxSocketTimeoutInMillis;
        return this;
    }

    public Long maxFutureTimeoutInMillis() {
        if (maxFutureTimeoutInMillis == null) {
            maxFutureTimeoutInMillis = ConfigurationProperties.maxFutureTimeout();
        }
        return maxFutureTimeoutInMillis;
    }

    public Configuration maxFutureTimeoutInMillis(Long maxFutureTimeoutInMillis) {
        this.maxFutureTimeoutInMillis = maxFutureTimeoutInMillis;
        return this;
    }

    public Integer socketConnectionTimeoutInMillis() {
        if (socketConnectionTimeoutInMillis == null) {
            socketConnectionTimeoutInMillis = ConfigurationProperties.socketConnectionTimeout();
        }
        return socketConnectionTimeoutInMillis;
    }

    public Configuration socketConnectionTimeoutInMillis(Integer socketConnectionTimeoutInMillis) {
        this.socketConnectionTimeoutInMillis = socketConnectionTimeoutInMillis;
        return this;
    }

    public Boolean alwaysCloseSocketConnections() {
        if (alwaysCloseSocketConnections == null) {
            alwaysCloseSocketConnections = ConfigurationProperties.alwaysCloseSocketConnections();
        }
        return alwaysCloseSocketConnections;
    }

    public Configuration alwaysCloseSocketConnections(Boolean alwaysCloseSocketConnections) {
        this.alwaysCloseSocketConnections = alwaysCloseSocketConnections;
        return this;
    }

    public Boolean useSemicolonAsQueryParameterSeparator() {
        if (useSemicolonAsQueryParameterSeparator == null) {
            useSemicolonAsQueryParameterSeparator = ConfigurationProperties.useSemicolonAsQueryParameterSeparator();
        }
        return useSemicolonAsQueryParameterSeparator;
    }

    public Configuration useSemicolonAsQueryParameterSeparator(Boolean useSemicolonAsQueryParameterSeparator) {
        this.useSemicolonAsQueryParameterSeparator = useSemicolonAsQueryParameterSeparator;
        return this;
    }

    public String sslCertificateDomainName() {
        if (sslCertificateDomainName == null) {
            sslCertificateDomainName = ConfigurationProperties.sslCertificateDomainName();
        }
        return sslCertificateDomainName;
    }

    public Configuration sslCertificateDomainName(String sslCertificateDomainName) {
        this.sslCertificateDomainName = sslCertificateDomainName;
        return this;
    }

    public String[] sslSubjectAlternativeNameDomains() {
        if (sslSubjectAlternativeNameDomains == null) {
            sslSubjectAlternativeNameDomains = ConfigurationProperties.sslSubjectAlternativeNameDomains();
        }
        return sslSubjectAlternativeNameDomains;
    }

    public Configuration sslSubjectAlternativeNameDomains(String[] sslSubjectAlternativeNameDomains) {
        this.sslSubjectAlternativeNameDomains = sslSubjectAlternativeNameDomains;
        return this;
    }

    public String[] sslSubjectAlternativeNameIps() {
        if (sslSubjectAlternativeNameIps == null) {
            sslSubjectAlternativeNameIps = ConfigurationProperties.sslSubjectAlternativeNameIps();
        }
        return sslSubjectAlternativeNameIps;
    }

    public Configuration sslSubjectAlternativeNameIps(String[] sslSubjectAlternativeNameIps) {
        this.sslSubjectAlternativeNameIps = sslSubjectAlternativeNameIps;
        return this;
    }

    public Boolean useBouncyCastleForKeyAndCertificateGeneration() {
        if (useBouncyCastleForKeyAndCertificateGeneration == null) {
            useBouncyCastleForKeyAndCertificateGeneration = ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration();
        }
        return useBouncyCastleForKeyAndCertificateGeneration;
    }

    public Configuration useBouncyCastleForKeyAndCertificateGeneration(Boolean useBouncyCastleForKeyAndCertificateGeneration) {
        this.useBouncyCastleForKeyAndCertificateGeneration = useBouncyCastleForKeyAndCertificateGeneration;
        return this;
    }

    public Boolean preventCertificateDynamicUpdate() {
        if (preventCertificateDynamicUpdate == null) {
            preventCertificateDynamicUpdate = ConfigurationProperties.preventCertificateDynamicUpdate();
        }
        return preventCertificateDynamicUpdate;
    }

    public Configuration preventCertificateDynamicUpdate(Boolean preventCertificateDynamicUpdate) {
        this.preventCertificateDynamicUpdate = preventCertificateDynamicUpdate;
        return this;
    }

    public String certificateAuthorityPrivateKey() {
        if (certificateAuthorityPrivateKey == null) {
            certificateAuthorityPrivateKey = ConfigurationProperties.certificateAuthorityPrivateKey();
        }
        return certificateAuthorityPrivateKey;
    }

    public Configuration certificateAuthorityPrivateKey(String certificateAuthorityPrivateKey) {
        this.certificateAuthorityPrivateKey = certificateAuthorityPrivateKey;
        return this;
    }

    public String certificateAuthorityCertificate() {
        if (certificateAuthorityCertificate == null) {
            certificateAuthorityCertificate = ConfigurationProperties.certificateAuthorityCertificate();
        }
        return certificateAuthorityCertificate;
    }

    public Configuration certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        this.certificateAuthorityCertificate = certificateAuthorityCertificate;
        return this;
    }

    public Boolean dynamicallyCreateCertificateAuthorityCertificate() {
        if (dynamicallyCreateCertificateAuthorityCertificate == null) {
            dynamicallyCreateCertificateAuthorityCertificate = ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate();
        }
        return dynamicallyCreateCertificateAuthorityCertificate;
    }

    public Configuration dynamicallyCreateCertificateAuthorityCertificate(Boolean dynamicallyCreateCertificateAuthorityCertificate) {
        this.dynamicallyCreateCertificateAuthorityCertificate = dynamicallyCreateCertificateAuthorityCertificate;
        return this;
    }

    public String directoryToSaveDynamicSSLCertificate() {
        if (directoryToSaveDynamicSSLCertificate == null) {
            directoryToSaveDynamicSSLCertificate = ConfigurationProperties.directoryToSaveDynamicSSLCertificate();
        }
        return directoryToSaveDynamicSSLCertificate;
    }

    public Configuration directoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        this.directoryToSaveDynamicSSLCertificate = directoryToSaveDynamicSSLCertificate;
        return this;
    }

    public Boolean proactivelyInitialiseTLS() {
        if (proactivelyInitialiseTLS == null) {
            proactivelyInitialiseTLS = ConfigurationProperties.proactivelyInitialiseTLS();
        }
        return proactivelyInitialiseTLS;
    }

    public Configuration proactivelyInitialiseTLS(Boolean proactivelyInitialiseTLS) {
        this.proactivelyInitialiseTLS = proactivelyInitialiseTLS;
        return this;
    }

    public String privateKeyPath() {
        if (privateKeyPath == null) {
            privateKeyPath = ConfigurationProperties.privateKeyPath();
        }
        return privateKeyPath;
    }

    public Configuration privateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
        return this;
    }

    public String x509CertificatePath() {
        if (x509CertificatePath == null) {
            x509CertificatePath = ConfigurationProperties.x509CertificatePath();
        }
        return x509CertificatePath;
    }

    public Configuration x509CertificatePath(String x509CertificatePath) {
        this.x509CertificatePath = x509CertificatePath;
        return this;
    }

    public Boolean tlsMutualAuthenticationRequired() {
        if (tlsMutualAuthenticationRequired == null) {
            tlsMutualAuthenticationRequired = ConfigurationProperties.tlsMutualAuthenticationRequired();
        }
        return tlsMutualAuthenticationRequired;
    }

    public Configuration tlsMutualAuthenticationRequired(Boolean tlsMutualAuthenticationRequired) {
        this.tlsMutualAuthenticationRequired = tlsMutualAuthenticationRequired;
        return this;
    }

    public String tlsMutualAuthenticationCertificateChain() {
        if (tlsMutualAuthenticationCertificateChain == null) {
            tlsMutualAuthenticationCertificateChain = ConfigurationProperties.tlsMutualAuthenticationCertificateChain();
        }
        return tlsMutualAuthenticationCertificateChain;
    }

    public Configuration tlsMutualAuthenticationCertificateChain(String tlsMutualAuthenticationCertificateChain) {
        this.tlsMutualAuthenticationCertificateChain = tlsMutualAuthenticationCertificateChain;
        return this;
    }

    public ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType() {
        if (forwardProxyTLSX509CertificatesTrustManagerType == null) {
            forwardProxyTLSX509CertificatesTrustManagerType = ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();
        }
        return forwardProxyTLSX509CertificatesTrustManagerType;
    }

    public Configuration forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType) {
        this.forwardProxyTLSX509CertificatesTrustManagerType = forwardProxyTLSX509CertificatesTrustManagerType;
        return this;
    }

    public String forwardProxyTLSCustomTrustX509Certificates() {
        if (forwardProxyTLSCustomTrustX509Certificates == null) {
            forwardProxyTLSCustomTrustX509Certificates = ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates();
        }
        return forwardProxyTLSCustomTrustX509Certificates;
    }

    public Configuration forwardProxyTLSCustomTrustX509Certificates(String forwardProxyTLSCustomTrustX509Certificates) {
        this.forwardProxyTLSCustomTrustX509Certificates = forwardProxyTLSCustomTrustX509Certificates;
        return this;
    }

    public String forwardProxyPrivateKey() {
        if (forwardProxyPrivateKey == null) {
            forwardProxyPrivateKey = ConfigurationProperties.forwardProxyPrivateKey();
        }
        return forwardProxyPrivateKey;
    }

    public Configuration forwardProxyPrivateKey(String forwardProxyPrivateKey) {
        this.forwardProxyPrivateKey = forwardProxyPrivateKey;
        return this;
    }

    public String forwardProxyCertificateChain() {
        if (forwardProxyCertificateChain == null) {
            forwardProxyCertificateChain = ConfigurationProperties.forwardProxyCertificateChain();
        }
        return forwardProxyCertificateChain;
    }

    public Configuration forwardProxyCertificateChain(String forwardProxyCertificateChain) {
        this.forwardProxyCertificateChain = forwardProxyCertificateChain;
        return this;
    }

    public Boolean controlPlaneTLSMutualAuthenticationRequired() {
        if (controlPlaneTLSMutualAuthenticationRequired == null) {
            controlPlaneTLSMutualAuthenticationRequired = ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired();
        }
        return controlPlaneTLSMutualAuthenticationRequired;
    }

    public Configuration controlPlaneTLSMutualAuthenticationRequired(Boolean controlPlaneTLSMutualAuthenticationRequired) {
        this.controlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired;
        return this;
    }

    public String controlPlaneTLSMutualAuthenticationCAChain() {
        if (controlPlaneTLSMutualAuthenticationCAChain == null) {
            controlPlaneTLSMutualAuthenticationCAChain = ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain();
        }
        return controlPlaneTLSMutualAuthenticationCAChain;
    }

    public Configuration controlPlaneTLSMutualAuthenticationCAChain(String controlPlaneTLSMutualAuthenticationCAChain) {
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
        return this;
    }

    public String controlPlanePrivateKeyPath() {
        if (controlPlanePrivateKeyPath == null) {
            controlPlanePrivateKeyPath = ConfigurationProperties.controlPlanePrivateKeyPath();
        }
        return controlPlanePrivateKeyPath;
    }

    public Configuration controlPlanePrivateKeyPath(String controlPlanePrivateKeyPath) {
        this.controlPlanePrivateKeyPath = controlPlanePrivateKeyPath;
        return this;
    }

    public String controlPlaneX509CertificatePath() {
        if (controlPlaneX509CertificatePath == null) {
            controlPlaneX509CertificatePath = ConfigurationProperties.controlPlaneX509CertificatePath();
        }
        return controlPlaneX509CertificatePath;
    }

    public Configuration controlPlaneX509CertificatePath(String controlPlaneX509CertificatePath) {
        this.controlPlaneX509CertificatePath = controlPlaneX509CertificatePath;
        return this;
    }

    public Boolean controlPlaneJWTAuthenticationRequired() {
        if (controlPlaneJWTAuthenticationRequired == null) {
            controlPlaneJWTAuthenticationRequired = ConfigurationProperties.controlPlaneJWTAuthenticationRequired();
        }
        return controlPlaneJWTAuthenticationRequired;
    }

    public Configuration controlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
        this.controlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired;
        return this;
    }

    public String controlPlaneJWTAuthenticationJWKSource() {
        if (controlPlaneJWTAuthenticationJWKSource == null) {
            controlPlaneJWTAuthenticationJWKSource = ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource();
        }
        return controlPlaneJWTAuthenticationJWKSource;
    }

    public Configuration controlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        this.controlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource;
        return this;
    }

    public Level logLevel() {
        if (logLevel == null) {
            logLevel = ConfigurationProperties.logLevel();
        }
        return logLevel;
    }

    public Configuration logLevel(Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public Boolean disableSystemOut() {
        if (disableSystemOut == null) {
            disableSystemOut = ConfigurationProperties.disableSystemOut();
        }
        return disableSystemOut;
    }

    public Configuration disableSystemOut(Boolean disableSystemOut) {
        this.disableSystemOut = disableSystemOut;
        return this;
    }

    public Boolean disableLogging() {
        if (disableLogging == null) {
            disableLogging = ConfigurationProperties.disableLogging();
        }
        return disableLogging;
    }

    public Configuration disableLogging(Boolean disableLogging) {
        this.disableLogging = disableLogging;
        return this;
    }

    public Boolean detailedMatchFailures() {
        if (detailedMatchFailures == null) {
            detailedMatchFailures = ConfigurationProperties.detailedMatchFailures();
        }
        return detailedMatchFailures;
    }

    public Configuration detailedMatchFailures(Boolean detailedMatchFailures) {
        this.detailedMatchFailures = detailedMatchFailures;
        return this;
    }

    public Boolean launchUIForLogLevelDebug() {
        if (launchUIForLogLevelDebug == null) {
            launchUIForLogLevelDebug = ConfigurationProperties.launchUIForLogLevelDebug();
        }
        return launchUIForLogLevelDebug;
    }

    public Configuration launchUIForLogLevelDebug(Boolean launchUIForLogLevelDebug) {
        this.launchUIForLogLevelDebug = launchUIForLogLevelDebug;
        return this;
    }

    public Boolean matchersFailFast() {
        if (matchersFailFast == null) {
            matchersFailFast = ConfigurationProperties.matchersFailFast();
        }
        return matchersFailFast;
    }

    public Configuration matchersFailFast(Boolean matchersFailFast) {
        this.matchersFailFast = matchersFailFast;
        return this;
    }

    public Boolean metricsEnabled() {
        if (metricsEnabled == null) {
            metricsEnabled = ConfigurationProperties.metricsEnabled();
        }
        return metricsEnabled;
    }

    public Configuration metricsEnabled(Boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public String localBoundIP() {
        if (localBoundIP == null) {
            localBoundIP = ConfigurationProperties.localBoundIP();
        }
        return localBoundIP;
    }

    public Configuration localBoundIP(String localBoundIP) {
        this.localBoundIP = localBoundIP;
        return this;
    }

    public Boolean attemptToProxyIfNoMatchingExpectation() {
        if (attemptToProxyIfNoMatchingExpectation == null) {
            attemptToProxyIfNoMatchingExpectation = ConfigurationProperties.attemptToProxyIfNoMatchingExpectation();
        }
        return attemptToProxyIfNoMatchingExpectation;
    }

    public Configuration attemptToProxyIfNoMatchingExpectation(Boolean attemptToProxyIfNoMatchingExpectation) {
        this.attemptToProxyIfNoMatchingExpectation = attemptToProxyIfNoMatchingExpectation;
        return this;
    }

    public InetSocketAddress forwardHttpProxy() {
        if (forwardHttpProxy == null) {
            forwardHttpProxy = ConfigurationProperties.forwardHttpProxy();
        }
        return forwardHttpProxy;
    }

    public Configuration forwardHttpProxy(InetSocketAddress forwardHttpProxy) {
        this.forwardHttpProxy = forwardHttpProxy;
        return this;
    }

    public InetSocketAddress forwardHttpsProxy() {
        if (forwardHttpsProxy == null) {
            forwardHttpsProxy = ConfigurationProperties.forwardHttpsProxy();
        }
        return forwardHttpsProxy;
    }

    public Configuration forwardHttpsProxy(InetSocketAddress forwardHttpsProxy) {
        this.forwardHttpsProxy = forwardHttpsProxy;
        return this;
    }

    public InetSocketAddress forwardSocksProxy() {
        if (forwardSocksProxy == null) {
            forwardSocksProxy = ConfigurationProperties.forwardSocksProxy();
        }
        return forwardSocksProxy;
    }

    public Configuration forwardSocksProxy(InetSocketAddress forwardSocksProxy) {
        this.forwardSocksProxy = forwardSocksProxy;
        return this;
    }

    public String forwardProxyAuthenticationUsername() {
        if (forwardProxyAuthenticationUsername == null) {
            forwardProxyAuthenticationUsername = ConfigurationProperties.forwardProxyAuthenticationUsername();
        }
        return forwardProxyAuthenticationUsername;
    }

    public Configuration forwardProxyAuthenticationUsername(String forwardProxyAuthenticationUsername) {
        this.forwardProxyAuthenticationUsername = forwardProxyAuthenticationUsername;
        return this;
    }

    public String forwardProxyAuthenticationPassword() {
        if (forwardProxyAuthenticationPassword == null) {
            forwardProxyAuthenticationPassword = ConfigurationProperties.forwardProxyAuthenticationPassword();
        }
        return forwardProxyAuthenticationPassword;
    }

    public Configuration forwardProxyAuthenticationPassword(String forwardProxyAuthenticationPassword) {
        this.forwardProxyAuthenticationPassword = forwardProxyAuthenticationPassword;
        return this;
    }

    public String proxyAuthenticationRealm() {
        if (proxyAuthenticationRealm == null) {
            proxyAuthenticationRealm = ConfigurationProperties.proxyAuthenticationRealm();
        }
        return proxyAuthenticationRealm;
    }

    public Configuration proxyAuthenticationRealm(String proxyAuthenticationRealm) {
        this.proxyAuthenticationRealm = proxyAuthenticationRealm;
        return this;
    }

    public String proxyAuthenticationUsername() {
        if (proxyAuthenticationUsername == null) {
            proxyAuthenticationUsername = ConfigurationProperties.proxyAuthenticationUsername();
        }
        return proxyAuthenticationUsername;
    }

    public Configuration proxyAuthenticationUsername(String proxyAuthenticationUsername) {
        this.proxyAuthenticationUsername = proxyAuthenticationUsername;
        return this;
    }

    public String proxyAuthenticationPassword() {
        if (proxyAuthenticationPassword == null) {
            proxyAuthenticationPassword = ConfigurationProperties.proxyAuthenticationPassword();
        }
        return proxyAuthenticationPassword;
    }

    public Configuration proxyAuthenticationPassword(String proxyAuthenticationPassword) {
        this.proxyAuthenticationPassword = proxyAuthenticationPassword;
        return this;
    }

    public String initializationClass() {
        if (initializationClass == null) {
            initializationClass = ConfigurationProperties.initializationClass();
        }
        return initializationClass;
    }

    public Configuration initializationClass(String initializationClass) {
        this.initializationClass = initializationClass;
        return this;
    }

    public String initializationJsonPath() {
        if (initializationJsonPath == null) {
            initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        }
        return initializationJsonPath;
    }

    public Configuration initializationJsonPath(String initializationJsonPath) {
        this.initializationJsonPath = initializationJsonPath;
        return this;
    }

    public Boolean watchInitializationJson() {
        if (watchInitializationJson == null) {
            watchInitializationJson = ConfigurationProperties.watchInitializationJson();
        }
        return watchInitializationJson;
    }

    public Configuration watchInitializationJson(Boolean watchInitializationJson) {
        this.watchInitializationJson = watchInitializationJson;
        return this;
    }

    public Boolean persistExpectations() {
        if (persistExpectations == null) {
            persistExpectations = ConfigurationProperties.persistExpectations();
        }
        return persistExpectations;
    }

    public Configuration persistExpectations(Boolean persistExpectations) {
        this.persistExpectations = persistExpectations;
        return this;
    }

    public String persistedExpectationsPath() {
        if (persistedExpectationsPath == null) {
            persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        }
        return persistedExpectationsPath;
    }

    public Configuration persistedExpectationsPath(String persistedExpectationsPath) {
        this.persistedExpectationsPath = persistedExpectationsPath;
        return this;
    }

    public Boolean enableCORSForAPI() {
        if (enableCORSForAPI == null) {
            enableCORSForAPI = ConfigurationProperties.enableCORSForAPI();
        }
        return enableCORSForAPI;
    }

    public Configuration enableCORSForAPI(Boolean enableCORSForAPI) {
        this.enableCORSForAPI = enableCORSForAPI;
        return this;
    }

    public Boolean enableCORSForAllResponses() {
        if (enableCORSForAllResponses == null) {
            enableCORSForAllResponses = ConfigurationProperties.enableCORSForAllResponses();
        }
        return enableCORSForAllResponses;
    }

    public Configuration enableCORSForAllResponses(Boolean enableCORSForAllResponses) {
        this.enableCORSForAllResponses = enableCORSForAllResponses;
        return this;
    }

    public String corsAllowHeaders() {
        if (corsAllowHeaders == null) {
            corsAllowHeaders = ConfigurationProperties.corsAllowHeaders();
        }
        return corsAllowHeaders;
    }

    public Configuration corsAllowHeaders(String corsAllowHeaders) {
        this.corsAllowHeaders = corsAllowHeaders;
        return this;
    }

    public String corsAllowMethods() {
        if (corsAllowMethods == null) {
            corsAllowMethods = ConfigurationProperties.corsAllowMethods();
        }
        return corsAllowMethods;
    }

    public Configuration corsAllowMethods(String corsAllowMethods) {
        this.corsAllowMethods = corsAllowMethods;
        return this;
    }

    public Boolean corsAllowCredentials() {
        if (corsAllowCredentials == null) {
            corsAllowCredentials = ConfigurationProperties.corsAllowCredentials();
        }
        return corsAllowCredentials;
    }

    public Configuration corsAllowCredentials(Boolean corsAllowCredentials) {
        this.corsAllowCredentials = corsAllowCredentials;
        return this;
    }

    public Integer corsMaxAgeInSeconds() {
        if (corsMaxAgeInSeconds == null) {
            corsMaxAgeInSeconds = ConfigurationProperties.corsMaxAgeInSeconds();
        }
        return corsMaxAgeInSeconds;
    }

    public Configuration corsMaxAgeInSeconds(Integer corsMaxAgeInSeconds) {
        this.corsMaxAgeInSeconds = corsMaxAgeInSeconds;
        return this;
    }

    public String livenessHttpGetPath() {
        if (livenessHttpGetPath == null) {
            livenessHttpGetPath = ConfigurationProperties.livenessHttpGetPath();
        }
        return livenessHttpGetPath;
    }

    public Configuration livenessHttpGetPath(String livenessHttpGetPath) {
        this.livenessHttpGetPath = livenessHttpGetPath;
        return this;
    }
}
