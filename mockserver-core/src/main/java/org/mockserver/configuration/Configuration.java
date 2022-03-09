package org.mockserver.configuration;

import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public class Configuration {

    public static Configuration configuration() {
        return new Configuration();
    }

    // logging
    private Level logLevel;
    private Boolean disableSystemOut;
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

    // verification
    private Integer maximumNumberOfRequestToReturnInVerificationFailure;

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

    // control plane authentication
    // TODO(jamesdbloom) missing from html
    private Boolean controlPlaneTLSMutualAuthenticationRequired;
    private String controlPlaneTLSMutualAuthenticationCAChain;
    private String controlPlanePrivateKeyPath;
    private String controlPlaneX509CertificatePath;
    private Boolean controlPlaneJWTAuthenticationRequired;
    private String controlPlaneJWTAuthenticationJWKSource;

    // TLS
    private Boolean useBouncyCastleForKeyAndCertificateGeneration;
    // TODO(jamesdbloom) missing from html
    private Boolean proactivelyInitialiseTLS;
    private boolean rebuildTLSContext;
    private boolean rebuildServerTLSContext;

    // inbound - dynamic CA
    private Boolean dynamicallyCreateCertificateAuthorityCertificate;
    private String directoryToSaveDynamicSSLCertificate;

    // inbound - dynamic private key & x509
    private Boolean preventCertificateDynamicUpdate;
    private String sslCertificateDomainName;
    private Set<String> sslSubjectAlternativeNameDomains;
    private Set<String> sslSubjectAlternativeNameIps;

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

    public Level logLevel() {
        if (logLevel == null) {
            return ConfigurationProperties.logLevel();
        }
        return logLevel;
    }

    /**
     * Override the default logging level of INFO
     *
     * @param level the log level, which can be TRACE, DEBUG, INFO, WARN, ERROR, OFF, FINEST, FINE, INFO, WARNING, SEVERE
     */
    public Configuration logLevel(Level level) {
        this.logLevel = level;
        return this;
    }

    public Boolean disableSystemOut() {
        if (disableSystemOut == null) {
            return ConfigurationProperties.disableSystemOut();
        }
        return disableSystemOut;
    }

    /**
     * Disable printing log to system out for JVM, default is enabled
     *
     * @param disableSystemOut printing log to system out for JVM
     */
    public Configuration disableSystemOut(Boolean disableSystemOut) {
        this.disableSystemOut = disableSystemOut;
        return this;
    }

    public Boolean detailedMatchFailures() {
        if (detailedMatchFailures == null) {
            return ConfigurationProperties.detailedMatchFailures();
        }
        return detailedMatchFailures;
    }

    /**
     * If true (the default) the log event recording that a request matcher did not match will include a detailed reason why each non matching field did not match.
     *
     * @param detailedMatchFailures enabled detailed match failure log events
     */
    public Configuration detailedMatchFailures(Boolean detailedMatchFailures) {
        this.detailedMatchFailures = detailedMatchFailures;
        return this;
    }

    public Boolean launchUIForLogLevelDebug() {
        if (launchUIForLogLevelDebug == null) {
            return ConfigurationProperties.launchUIForLogLevelDebug();
        }
        return launchUIForLogLevelDebug;
    }

    /**
     * If true (the default) the ClientAndServer constructor will open the UI in the default browser when the log level is set to DEBUG.
     *
     * @param launchUIForLogLevelDebug enabled ClientAndServer constructor launching UI when log level is DEBUG
     */
    public Configuration launchUIForLogLevelDebug(Boolean launchUIForLogLevelDebug) {
        this.launchUIForLogLevelDebug = launchUIForLogLevelDebug;
        return this;
    }

    public Boolean metricsEnabled() {
        if (metricsEnabled == null) {
            return ConfigurationProperties.metricsEnabled();
        }
        return metricsEnabled;
    }

    /**
     * Enable gathering of metrics, default is false
     *
     * @param metricsEnabled enable metrics
     */
    public Configuration metricsEnabled(Boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public Integer maxExpectations() {
        if (maxExpectations == null) {
            return ConfigurationProperties.maxExpectations();
        }
        return maxExpectations;
    }

    /**
     * <p>
     * Maximum number of expectations stored in memory.  Expectations are stored in a circular queue so once this limit is reach the oldest and lowest priority expectations are overwritten
     * </p>
     * <p>
     * The default maximum depends on the available memory in the JVM with an upper limit of 5000, but can be overridden using defaultMaxExpectations
     *</p>
     * @param maxExpectations maximum number of expectations to store
     */
    public Configuration maxExpectations(Integer maxExpectations) {
        this.maxExpectations = maxExpectations;
        return this;
    }

    public Integer defaultMaxExpectations() {
        if (defaultMaxExpectations == null) {
            return ConfigurationProperties.defaultMaxExpectations();
        }
        return defaultMaxExpectations;
    }

    /**
     * <p>
     * Default maximum number of expectations stored in memory.  Expectations are stored in a circular queue so once this limit is reach the oldest and lowest priority expectations are overwritten
     * </p>
     * <p>
     * This default maximum depends on the available memory in the JVM with an upper limit of 5000
     *</p>
     * @param defaultMaxExpectations maximum number of expectations to store
     */
    public Configuration defaultMaxExpectations(Integer defaultMaxExpectations) {
        this.defaultMaxExpectations = defaultMaxExpectations;
        return this;
    }

    public Integer maxLogEntries() {
        if (maxLogEntries == null) {
            return ConfigurationProperties.maxLogEntries();
        }
        return maxLogEntries;
    }

    /**
     * <p>
     * Maximum number of log entries stored in memory.  Log entries are stored in a circular queue so once this limit is reach the oldest log entries are overwritten
     * </p>
     * <p>
     * The default maximum depends on the available memory in the JVM with an upper limit of 60000, but can be overridden using defaultMaxLogEntries
     *</p>
     * @param maxLogEntries maximum number of expectations to store
     */
    public Configuration maxLogEntries(Integer maxLogEntries) {
        this.maxLogEntries = maxLogEntries;
        return this;
    }

    public Integer defaultMaxLogEntries() {
        if (defaultMaxLogEntries == null) {
            return ConfigurationProperties.defaultMaxLogEntries();
        }
        return defaultMaxLogEntries;
    }

    /**
     * <p>
     * Maximum number of log entries stored in memory.  Log entries are stored in a circular queue so once this limit is reach the oldest log entries are overwritten
     * </p>
     * <p>
     * The default maximum depends on the available memory in the JVM with an upper limit of 60000
     *</p>
     * @param defaultMaxLogEntries maximum number of expectations to store
     */
    public Configuration defaultMaxLogEntries(Integer defaultMaxLogEntries) {
        this.defaultMaxLogEntries = defaultMaxLogEntries;
        return this;
    }

    public Integer maxWebSocketExpectations() {
        if (maxWebSocketExpectations == null) {
            return ConfigurationProperties.maxWebSocketExpectations();
        }
        return maxWebSocketExpectations;
    }

    /**
     * <p>
     * Maximum number of remote (not the same JVM) method callbacks (i.e. web sockets) registered for expectations.  The web socket client registry entries are stored in a circular queue so once this limit is reach the oldest are overwritten.
     * </p>
     * <p>
     * The default is 1500
     * </p>
     *
     * @param maxWebSocketExpectations maximum number of method callbacks (i.e. web sockets) registered for expectations
     */
    public Configuration maxWebSocketExpectations(Integer maxWebSocketExpectations) {
        this.maxWebSocketExpectations = maxWebSocketExpectations;
        return this;
    }

    public Boolean outputMemoryUsageCsv() {
        if (outputMemoryUsageCsv == null) {
            return ConfigurationProperties.outputMemoryUsageCsv();
        }
        return outputMemoryUsageCsv;
    }

    /**
     * <p>Output JVM memory usage metrics to CSV file periodically called <strong>memoryUsage_&lt;yyyy-MM-dd&gt;.csv</strong></p>
     *
     * @param outputMemoryUsageCsv output of JVM memory metrics
     */
    public Configuration outputMemoryUsageCsv(Boolean outputMemoryUsageCsv) {
        this.outputMemoryUsageCsv = outputMemoryUsageCsv;
        return this;
    }

    public String memoryUsageCsvDirectory() {
        if (memoryUsageCsvDirectory == null) {
            return ConfigurationProperties.memoryUsageCsvDirectory();
        }
        return memoryUsageCsvDirectory;
    }

    /**
     * <p>Directory to output JVM memory usage metrics CSV files to when outputMemoryUsageCsv enabled</p>
     *
     * @param memoryUsageCsvDirectory directory to save JVM memory metrics CSV files
     */
    public Configuration memoryUsageCsvDirectory(String memoryUsageCsvDirectory) {
        this.memoryUsageCsvDirectory = memoryUsageCsvDirectory;
        return this;
    }

    public Integer nioEventLoopThreadCount() {
        if (nioEventLoopThreadCount == null) {
            return ConfigurationProperties.nioEventLoopThreadCount();
        }
        return nioEventLoopThreadCount;
    }

    /**
     * <p>Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic, long running tasks are done on the action handler thread pool.</p>
     *
     * @param nioEventLoopThreadCount Netty worker thread pool size
     */
    public Configuration nioEventLoopThreadCount(Integer nioEventLoopThreadCount) {
        this.nioEventLoopThreadCount = nioEventLoopThreadCount;
        return this;
    }

    public Integer actionHandlerThreadCount() {
        if (actionHandlerThreadCount == null) {
            return ConfigurationProperties.actionHandlerThreadCount();
        }
        return actionHandlerThreadCount;
    }

    /**
     * <p>Number of threads for the action handler thread pool</p>
     * <p>These threads are used for handling actions such as:</p>
     *     <ul>
     *         <li>serialising and writing expectation or proxied responses</li>
     *         <li>handling response delays in a non-blocking way (i.e. using a scheduler)</li>
     *         <li>executing class callbacks</li>
     *         <li>handling method / closure callbacks (using web sockets)</li>
     *     </ul>
     * <p>
     * <p>Default is maximum of 5 or available processors count</p>
     *
     * @param actionHandlerThreadCount Netty worker thread pool size
     */
    public Configuration actionHandlerThreadCount(Integer actionHandlerThreadCount) {
        this.actionHandlerThreadCount = actionHandlerThreadCount;
        return this;
    }

    public Integer webSocketClientEventLoopThreadCount() {
        if (webSocketClientEventLoopThreadCount == null) {
            return ConfigurationProperties.webSocketClientEventLoopThreadCount();
        }
        return webSocketClientEventLoopThreadCount;
    }

    /**
     * <p>Client Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic.</p>
     *
     * <p>Default is 5 threads</p>
     *
     * @param webSocketClientEventLoopThreadCount Client Netty worker thread pool size
     */
    public Configuration webSocketClientEventLoopThreadCount(Integer webSocketClientEventLoopThreadCount) {
        this.webSocketClientEventLoopThreadCount = webSocketClientEventLoopThreadCount;
        return this;
    }

    public Integer clientNioEventLoopThreadCount() {
        if (clientNioEventLoopThreadCount == null) {
            return ConfigurationProperties.clientNioEventLoopThreadCount();
        }
        return clientNioEventLoopThreadCount;
    }

    /**
     * <p>Client Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic.</p>
     *
     * <p>Default is 5 threads</p>
     *
     * @param clientNioEventLoopThreadCount Client Netty worker thread pool size
     */
    public Configuration clientNioEventLoopThreadCount(Integer clientNioEventLoopThreadCount) {
        this.clientNioEventLoopThreadCount = clientNioEventLoopThreadCount;
        return this;
    }

    public Long maxFutureTimeoutInMillis() {
        if (maxFutureTimeoutInMillis == null) {
            return ConfigurationProperties.maxFutureTimeout();
        }
        return maxFutureTimeoutInMillis;
    }

    /**
     * Maximum time allowed in milliseconds for any future to wait, for example when waiting for a response over a web socket callback.
     *
     * Default is 60,000 ms
     *
     * @param maxFutureTimeoutInMillis maximum time allowed in milliseconds
     */
    public Configuration maxFutureTimeoutInMillis(Long maxFutureTimeoutInMillis) {
        this.maxFutureTimeoutInMillis = maxFutureTimeoutInMillis;
        return this;
    }

    public Boolean matchersFailFast() {
        if (matchersFailFast == null) {
            return ConfigurationProperties.matchersFailFast();
        }
        return matchersFailFast;
    }

    /**
     * If true (the default) request matchers will fail on the first non-matching field, if false request matchers will compare all fields.
     * This is useful to see all mismatching fields in the log event recording that a request matcher did not match.
     *
     * @param matchersFailFast enabled request matchers failing fast
     */
    public Configuration matchersFailFast(Boolean matchersFailFast) {
        this.matchersFailFast = matchersFailFast;
        return this;
    }

    public Long maxSocketTimeoutInMillis() {
        if (maxSocketTimeoutInMillis == null) {
            return ConfigurationProperties.maxSocketTimeout();
        }
        return maxSocketTimeoutInMillis;
    }

    /**
     * Maximum time in milliseconds allowed for a response from a socket
     *
     * Default is 20,000 ms
     *
     * @param maxSocketTimeoutInMillis maximum time in milliseconds allowed
     */
    public Configuration maxSocketTimeoutInMillis(Long maxSocketTimeoutInMillis) {
        this.maxSocketTimeoutInMillis = maxSocketTimeoutInMillis;
        return this;
    }

    public Integer socketConnectionTimeoutInMillis() {
        if (socketConnectionTimeoutInMillis == null) {
            return ConfigurationProperties.socketConnectionTimeout();
        }
        return socketConnectionTimeoutInMillis;
    }

    /**
     * Maximum time in milliseconds allowed to connect to a socket
     *
     * Default is 20,000 ms
     *
     * @param socketConnectionTimeoutInMillis maximum time allowed in milliseconds
     */
    public Configuration socketConnectionTimeoutInMillis(Integer socketConnectionTimeoutInMillis) {
        this.socketConnectionTimeoutInMillis = socketConnectionTimeoutInMillis;
        return this;
    }

    public Boolean alwaysCloseSocketConnections() {
        if (alwaysCloseSocketConnections == null) {
            return ConfigurationProperties.alwaysCloseSocketConnections();
        }
        return alwaysCloseSocketConnections;
    }

    public Configuration alwaysCloseSocketConnections(Boolean alwaysCloseSocketConnections) {
        this.alwaysCloseSocketConnections = alwaysCloseSocketConnections;
        return this;
    }

    public String localBoundIP() {
        if (localBoundIP == null) {
            return ConfigurationProperties.localBoundIP();
        }
        return localBoundIP;
    }

    public Configuration localBoundIP(String localBoundIP) {
        this.localBoundIP = localBoundIP;
        return this;
    }

    public Integer maxInitialLineLength() {
        if (maxInitialLineLength == null) {
            return ConfigurationProperties.maxInitialLineLength();
        }
        return maxInitialLineLength;
    }

    public Configuration maxInitialLineLength(Integer maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public Integer maxHeaderSize() {
        if (maxHeaderSize == null) {
            return ConfigurationProperties.maxHeaderSize();
        }
        return maxHeaderSize;
    }

    public Configuration maxHeaderSize(Integer maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
        return this;
    }

    public Integer maxChunkSize() {
        if (maxChunkSize == null) {
            return ConfigurationProperties.maxChunkSize();
        }
        return maxChunkSize;
    }

    public Configuration maxChunkSize(Integer maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public Boolean useSemicolonAsQueryParameterSeparator() {
        if (useSemicolonAsQueryParameterSeparator == null) {
            return ConfigurationProperties.useSemicolonAsQueryParameterSeparator();
        }
        return useSemicolonAsQueryParameterSeparator;
    }

    public Configuration useSemicolonAsQueryParameterSeparator(Boolean useSemicolonAsQueryParameterSeparator) {
        this.useSemicolonAsQueryParameterSeparator = useSemicolonAsQueryParameterSeparator;
        return this;
    }

    public Boolean enableCORSForAPI() {
        if (enableCORSForAPI == null) {
            return ConfigurationProperties.enableCORSForAPI();
        }
        return enableCORSForAPI;
    }

    public Configuration enableCORSForAPI(Boolean enableCORSForAPI) {
        this.enableCORSForAPI = enableCORSForAPI;
        return this;
    }

    public Boolean enableCORSForAllResponses() {
        if (enableCORSForAllResponses == null) {
            return ConfigurationProperties.enableCORSForAllResponses();
        }
        return enableCORSForAllResponses;
    }

    public Configuration enableCORSForAllResponses(Boolean enableCORSForAllResponses) {
        this.enableCORSForAllResponses = enableCORSForAllResponses;
        return this;
    }

    public String corsAllowHeaders() {
        if (corsAllowHeaders == null) {
            return ConfigurationProperties.corsAllowHeaders();
        }
        return corsAllowHeaders;
    }

    public Configuration corsAllowHeaders(String corsAllowHeaders) {
        this.corsAllowHeaders = corsAllowHeaders;
        return this;
    }

    public String corsAllowMethods() {
        if (corsAllowMethods == null) {
            return ConfigurationProperties.corsAllowMethods();
        }
        return corsAllowMethods;
    }

    public Configuration corsAllowMethods(String corsAllowMethods) {
        this.corsAllowMethods = corsAllowMethods;
        return this;
    }

    public Boolean corsAllowCredentials() {
        if (corsAllowCredentials == null) {
            return ConfigurationProperties.corsAllowCredentials();
        }
        return corsAllowCredentials;
    }

    public Configuration corsAllowCredentials(Boolean corsAllowCredentials) {
        this.corsAllowCredentials = corsAllowCredentials;
        return this;
    }

    public Integer corsMaxAgeInSeconds() {
        if (corsMaxAgeInSeconds == null) {
            return ConfigurationProperties.corsMaxAgeInSeconds();
        }
        return corsMaxAgeInSeconds;
    }

    public Configuration corsMaxAgeInSeconds(Integer corsMaxAgeInSeconds) {
        this.corsMaxAgeInSeconds = corsMaxAgeInSeconds;
        return this;
    }

    public String initializationClass() {
        if (initializationClass == null) {
            return ConfigurationProperties.initializationClass();
        }
        return initializationClass;
    }

    public Configuration initializationClass(String initializationClass) {
        this.initializationClass = initializationClass;
        return this;
    }

    public String initializationJsonPath() {
        if (initializationJsonPath == null) {
            return ConfigurationProperties.initializationJsonPath();
        }
        return initializationJsonPath;
    }

    public Configuration initializationJsonPath(String initializationJsonPath) {
        this.initializationJsonPath = initializationJsonPath;
        return this;
    }

    public Boolean watchInitializationJson() {
        if (watchInitializationJson == null) {
            return ConfigurationProperties.watchInitializationJson();
        }
        return watchInitializationJson;
    }

    public Configuration watchInitializationJson(Boolean watchInitializationJson) {
        this.watchInitializationJson = watchInitializationJson;
        return this;
    }

    public Boolean persistExpectations() {
        if (persistExpectations == null) {
            return ConfigurationProperties.persistExpectations();
        }
        return persistExpectations;
    }

    public Configuration persistExpectations(Boolean persistExpectations) {
        this.persistExpectations = persistExpectations;
        return this;
    }

    public String persistedExpectationsPath() {
        if (persistedExpectationsPath == null) {
            return ConfigurationProperties.persistedExpectationsPath();
        }
        return persistedExpectationsPath;
    }

    public Configuration persistedExpectationsPath(String persistedExpectationsPath) {
        this.persistedExpectationsPath = persistedExpectationsPath;
        return this;
    }

    public Integer maximumNumberOfRequestToReturnInVerificationFailure() {
        if (maximumNumberOfRequestToReturnInVerificationFailure == null) {
            return ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure();
        }
        return maximumNumberOfRequestToReturnInVerificationFailure;
    }

    public Configuration maximumNumberOfRequestToReturnInVerificationFailure(Integer maximumNumberOfRequestToReturnInVerificationFailure) {
        this.maximumNumberOfRequestToReturnInVerificationFailure = maximumNumberOfRequestToReturnInVerificationFailure;
        return this;
    }

    public Boolean attemptToProxyIfNoMatchingExpectation() {
        if (attemptToProxyIfNoMatchingExpectation == null) {
            return ConfigurationProperties.attemptToProxyIfNoMatchingExpectation();
        }
        return attemptToProxyIfNoMatchingExpectation;
    }

    public Configuration attemptToProxyIfNoMatchingExpectation(Boolean attemptToProxyIfNoMatchingExpectation) {
        this.attemptToProxyIfNoMatchingExpectation = attemptToProxyIfNoMatchingExpectation;
        return this;
    }

    public InetSocketAddress forwardHttpProxy() {
        if (forwardHttpProxy == null) {
            return ConfigurationProperties.forwardHttpProxy();
        }
        return forwardHttpProxy;
    }

    public Configuration forwardHttpProxy(InetSocketAddress forwardHttpProxy) {
        this.forwardHttpProxy = forwardHttpProxy;
        return this;
    }

    public InetSocketAddress forwardHttpsProxy() {
        if (forwardHttpsProxy == null) {
            return ConfigurationProperties.forwardHttpsProxy();
        }
        return forwardHttpsProxy;
    }

    public Configuration forwardHttpsProxy(InetSocketAddress forwardHttpsProxy) {
        this.forwardHttpsProxy = forwardHttpsProxy;
        return this;
    }

    public InetSocketAddress forwardSocksProxy() {
        if (forwardSocksProxy == null) {
            return ConfigurationProperties.forwardSocksProxy();
        }
        return forwardSocksProxy;
    }

    public Configuration forwardSocksProxy(InetSocketAddress forwardSocksProxy) {
        this.forwardSocksProxy = forwardSocksProxy;
        return this;
    }

    public String forwardProxyAuthenticationUsername() {
        if (forwardProxyAuthenticationUsername == null) {
            return ConfigurationProperties.forwardProxyAuthenticationUsername();
        }
        return forwardProxyAuthenticationUsername;
    }

    public Configuration forwardProxyAuthenticationUsername(String forwardProxyAuthenticationUsername) {
        this.forwardProxyAuthenticationUsername = forwardProxyAuthenticationUsername;
        return this;
    }

    public String forwardProxyAuthenticationPassword() {
        if (forwardProxyAuthenticationPassword == null) {
            return ConfigurationProperties.forwardProxyAuthenticationPassword();
        }
        return forwardProxyAuthenticationPassword;
    }

    public Configuration forwardProxyAuthenticationPassword(String forwardProxyAuthenticationPassword) {
        this.forwardProxyAuthenticationPassword = forwardProxyAuthenticationPassword;
        return this;
    }

    public String proxyAuthenticationRealm() {
        if (proxyAuthenticationRealm == null) {
            return ConfigurationProperties.proxyAuthenticationRealm();
        }
        return proxyAuthenticationRealm;
    }

    public Configuration proxyAuthenticationRealm(String proxyAuthenticationRealm) {
        this.proxyAuthenticationRealm = proxyAuthenticationRealm;
        return this;
    }

    public String proxyAuthenticationUsername() {
        if (proxyAuthenticationUsername == null) {
            return ConfigurationProperties.proxyAuthenticationUsername();
        }
        return proxyAuthenticationUsername;
    }

    public Configuration proxyAuthenticationUsername(String proxyAuthenticationUsername) {
        this.proxyAuthenticationUsername = proxyAuthenticationUsername;
        return this;
    }

    public String proxyAuthenticationPassword() {
        if (proxyAuthenticationPassword == null) {
            return ConfigurationProperties.proxyAuthenticationPassword();
        }
        return proxyAuthenticationPassword;
    }

    public Configuration proxyAuthenticationPassword(String proxyAuthenticationPassword) {
        this.proxyAuthenticationPassword = proxyAuthenticationPassword;
        return this;
    }

    public String livenessHttpGetPath() {
        if (livenessHttpGetPath == null) {
            return ConfigurationProperties.livenessHttpGetPath();
        }
        return livenessHttpGetPath;
    }

    public Configuration livenessHttpGetPath(String livenessHttpGetPath) {
        this.livenessHttpGetPath = livenessHttpGetPath;
        return this;
    }

    public Boolean controlPlaneTLSMutualAuthenticationRequired() {
        if (controlPlaneTLSMutualAuthenticationRequired == null) {
            return ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired();
        }
        return controlPlaneTLSMutualAuthenticationRequired;
    }

    public Configuration controlPlaneTLSMutualAuthenticationRequired(Boolean controlPlaneTLSMutualAuthenticationRequired) {
        this.controlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired;
        return this;
    }

    public String controlPlaneTLSMutualAuthenticationCAChain() {
        if (controlPlaneTLSMutualAuthenticationCAChain == null) {
            return ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain();
        }
        return controlPlaneTLSMutualAuthenticationCAChain;
    }

    public Configuration controlPlaneTLSMutualAuthenticationCAChain(String controlPlaneTLSMutualAuthenticationCAChain) {
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
        return this;
    }

    public String controlPlanePrivateKeyPath() {
        if (controlPlanePrivateKeyPath == null) {
            return ConfigurationProperties.controlPlanePrivateKeyPath();
        }
        return controlPlanePrivateKeyPath;
    }

    public Configuration controlPlanePrivateKeyPath(String controlPlanePrivateKeyPath) {
        this.controlPlanePrivateKeyPath = controlPlanePrivateKeyPath;
        return this;
    }

    public String controlPlaneX509CertificatePath() {
        if (controlPlaneX509CertificatePath == null) {
            return ConfigurationProperties.controlPlaneX509CertificatePath();
        }
        return controlPlaneX509CertificatePath;
    }

    public Configuration controlPlaneX509CertificatePath(String controlPlaneX509CertificatePath) {
        this.controlPlaneX509CertificatePath = controlPlaneX509CertificatePath;
        return this;
    }

    public Boolean controlPlaneJWTAuthenticationRequired() {
        if (controlPlaneJWTAuthenticationRequired == null) {
            return ConfigurationProperties.controlPlaneJWTAuthenticationRequired();
        }
        return controlPlaneJWTAuthenticationRequired;
    }

    public Configuration controlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
        this.controlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired;
        return this;
    }

    public String controlPlaneJWTAuthenticationJWKSource() {
        if (controlPlaneJWTAuthenticationJWKSource == null) {
            return ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource();
        }
        return controlPlaneJWTAuthenticationJWKSource;
    }

    public Configuration controlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        this.controlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource;
        return this;
    }

    public Boolean useBouncyCastleForKeyAndCertificateGeneration() {
        if (useBouncyCastleForKeyAndCertificateGeneration == null) {
            return ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration();
        }
        return useBouncyCastleForKeyAndCertificateGeneration;
    }

    public Configuration useBouncyCastleForKeyAndCertificateGeneration(Boolean useBouncyCastleForKeyAndCertificateGeneration) {
        this.useBouncyCastleForKeyAndCertificateGeneration = useBouncyCastleForKeyAndCertificateGeneration;
        return this;
    }

    public Boolean proactivelyInitialiseTLS() {
        if (proactivelyInitialiseTLS == null) {
            return ConfigurationProperties.proactivelyInitialiseTLS();
        }
        return proactivelyInitialiseTLS;
    }

    public Configuration proactivelyInitialiseTLS(Boolean proactivelyInitialiseTLS) {
        this.proactivelyInitialiseTLS = proactivelyInitialiseTLS;
        return this;
    }

    public boolean rebuildTLSContext() {
        return rebuildTLSContext;
    }

    public Configuration rebuildTLSContext(boolean rebuildTLSContext) {
        this.rebuildTLSContext = rebuildTLSContext;
        return this;
    }

    public boolean rebuildServerTLSContext() {
        return rebuildServerTLSContext;
    }

    public Configuration rebuildServerTLSContext(boolean rebuildServerTLSContext) {
        this.rebuildServerTLSContext = rebuildServerTLSContext;
        return this;
    }

    public Boolean dynamicallyCreateCertificateAuthorityCertificate() {
        if (dynamicallyCreateCertificateAuthorityCertificate == null) {
            return ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate();
        }
        return dynamicallyCreateCertificateAuthorityCertificate;
    }

    public Configuration dynamicallyCreateCertificateAuthorityCertificate(Boolean dynamicallyCreateCertificateAuthorityCertificate) {
        this.dynamicallyCreateCertificateAuthorityCertificate = dynamicallyCreateCertificateAuthorityCertificate;
        return this;
    }

    public String directoryToSaveDynamicSSLCertificate() {
        if (directoryToSaveDynamicSSLCertificate == null) {
            return ConfigurationProperties.directoryToSaveDynamicSSLCertificate();
        }
        return directoryToSaveDynamicSSLCertificate;
    }

    public Configuration directoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        this.directoryToSaveDynamicSSLCertificate = directoryToSaveDynamicSSLCertificate;
        return this;
    }

    public Boolean preventCertificateDynamicUpdate() {
        if (preventCertificateDynamicUpdate == null) {
            return ConfigurationProperties.preventCertificateDynamicUpdate();
        }
        return preventCertificateDynamicUpdate;
    }

    public Configuration preventCertificateDynamicUpdate(Boolean preventCertificateDynamicUpdate) {
        this.preventCertificateDynamicUpdate = preventCertificateDynamicUpdate;
        return this;
    }

    public String sslCertificateDomainName() {
        if (sslCertificateDomainName == null) {
            return ConfigurationProperties.sslCertificateDomainName();
        }
        return sslCertificateDomainName;
    }

    public Configuration sslCertificateDomainName(String sslCertificateDomainName) {
        this.sslCertificateDomainName = sslCertificateDomainName;
        return this;
    }

    public Set<String> sslSubjectAlternativeNameDomains() {
        if (sslSubjectAlternativeNameDomains == null) {
            return Sets.newConcurrentHashSet(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains().split(",")));
        }
        return sslSubjectAlternativeNameDomains;
    }

    public Configuration sslSubjectAlternativeNameDomains(String... sslSubjectAlternativeNameDomains) {
        this.sslSubjectAlternativeNameDomains = Sets.newConcurrentHashSet(Arrays.asList(sslSubjectAlternativeNameDomains));
        return this;
    }

    public Configuration sslSubjectAlternativeNameDomains(Set<String> sslSubjectAlternativeNameDomains) {
        this.sslSubjectAlternativeNameDomains = sslSubjectAlternativeNameDomains;
        return this;
    }

    public Set<String> sslSubjectAlternativeNameIps() {
        if (sslSubjectAlternativeNameIps == null) {
            return Sets.newConcurrentHashSet(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps().split(",")));
        }
        return sslSubjectAlternativeNameIps;
    }

    public Configuration sslSubjectAlternativeNameIps(String... sslSubjectAlternativeNameIps) {
        sslSubjectAlternativeNameIps(Sets.newConcurrentHashSet(Arrays.asList(sslSubjectAlternativeNameIps)));
        return this;
    }

    public Configuration sslSubjectAlternativeNameIps(Set<String> sslSubjectAlternativeNameIps) {
        this.sslSubjectAlternativeNameIps = sslSubjectAlternativeNameIps;
        return this;
    }

    public String certificateAuthorityPrivateKey() {
        if (certificateAuthorityPrivateKey == null) {
            return ConfigurationProperties.certificateAuthorityPrivateKey();
        }
        return certificateAuthorityPrivateKey;
    }

    public Configuration certificateAuthorityPrivateKey(String certificateAuthorityPrivateKey) {
        this.certificateAuthorityPrivateKey = certificateAuthorityPrivateKey;
        return this;
    }

    public String certificateAuthorityCertificate() {
        if (certificateAuthorityCertificate == null) {
            return ConfigurationProperties.certificateAuthorityCertificate();
        }
        return certificateAuthorityCertificate;
    }

    public Configuration certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        this.certificateAuthorityCertificate = certificateAuthorityCertificate;
        return this;
    }

    public String privateKeyPath() {
        if (privateKeyPath == null) {
            return ConfigurationProperties.privateKeyPath();
        }
        return privateKeyPath;
    }

    public Configuration privateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
        return this;
    }

    public String x509CertificatePath() {
        if (x509CertificatePath == null) {
            return ConfigurationProperties.x509CertificatePath();
        }
        return x509CertificatePath;
    }

    public Configuration x509CertificatePath(String x509CertificatePath) {
        this.x509CertificatePath = x509CertificatePath;
        return this;
    }

    public Boolean tlsMutualAuthenticationRequired() {
        if (tlsMutualAuthenticationRequired == null) {
            return ConfigurationProperties.tlsMutualAuthenticationRequired();
        }
        return tlsMutualAuthenticationRequired;
    }

    public Configuration tlsMutualAuthenticationRequired(Boolean tlsMutualAuthenticationRequired) {
        this.tlsMutualAuthenticationRequired = tlsMutualAuthenticationRequired;
        return this;
    }

    public String tlsMutualAuthenticationCertificateChain() {
        if (tlsMutualAuthenticationCertificateChain == null) {
            return ConfigurationProperties.tlsMutualAuthenticationCertificateChain();
        }
        return tlsMutualAuthenticationCertificateChain;
    }

    public Configuration tlsMutualAuthenticationCertificateChain(String tlsMutualAuthenticationCertificateChain) {
        this.tlsMutualAuthenticationCertificateChain = tlsMutualAuthenticationCertificateChain;
        return this;
    }

    public ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType() {
        if (forwardProxyTLSX509CertificatesTrustManagerType == null) {
            return ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();
        }
        return forwardProxyTLSX509CertificatesTrustManagerType;
    }

    public Configuration forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType) {
        this.forwardProxyTLSX509CertificatesTrustManagerType = forwardProxyTLSX509CertificatesTrustManagerType;
        return this;
    }

    public String forwardProxyTLSCustomTrustX509Certificates() {
        if (forwardProxyTLSCustomTrustX509Certificates == null) {
            return ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates();
        }
        return forwardProxyTLSCustomTrustX509Certificates;
    }

    public Configuration forwardProxyTLSCustomTrustX509Certificates(String forwardProxyTLSCustomTrustX509Certificates) {
        this.forwardProxyTLSCustomTrustX509Certificates = forwardProxyTLSCustomTrustX509Certificates;
        return this;
    }

    public String forwardProxyPrivateKey() {
        if (forwardProxyPrivateKey == null) {
            return ConfigurationProperties.forwardProxyPrivateKey();
        }
        return forwardProxyPrivateKey;
    }

    public Configuration forwardProxyPrivateKey(String forwardProxyPrivateKey) {
        this.forwardProxyPrivateKey = forwardProxyPrivateKey;
        return this;
    }

    public String forwardProxyCertificateChain() {
        if (forwardProxyCertificateChain == null) {
            return ConfigurationProperties.forwardProxyCertificateChain();
        }
        return forwardProxyCertificateChain;
    }

    public Configuration forwardProxyCertificateChain(String forwardProxyCertificateChain) {
        this.forwardProxyCertificateChain = forwardProxyCertificateChain;
        return this;
    }

    public void addSubjectAlternativeName(String host) {
        if (isNotBlank(host)) {
            String hostWithoutPort = substringBefore(host, ":");
            if (isNotBlank(hostWithoutPort)) {
                if (InetAddresses.isInetAddress(hostWithoutPort)) {
                    addSslSubjectAlternativeNameIps(hostWithoutPort);
                } else {
                    addSslSubjectAlternativeNameDomains(hostWithoutPort);
                }
            }
        }
    }

    public void addSslSubjectAlternativeNameIps(String... additionalSubjectAlternativeNameIps) {
        boolean subjectAlternativeIpsModified = false;
        for (String subjectAlternativeIp : additionalSubjectAlternativeNameIps) {
            if (sslSubjectAlternativeNameIps().add(subjectAlternativeIp.trim())) {
                subjectAlternativeIpsModified = true;
            }
        }
        if (subjectAlternativeIpsModified) {
            rebuildServerTLSContext(true);
        }
    }

    public void clearSslSubjectAlternativeNameIps() {
        sslSubjectAlternativeNameIps.clear();
    }

    public void addSslSubjectAlternativeNameDomains(String... additionalSubjectAlternativeNameDomains) {
        boolean subjectAlternativeDomainsModified = false;
        for (String subjectAlternativeDomain : additionalSubjectAlternativeNameDomains) {
            if (sslSubjectAlternativeNameDomains().add(subjectAlternativeDomain.trim())) {
                subjectAlternativeDomainsModified = true;
            }
        }
        if (subjectAlternativeDomainsModified) {
            rebuildServerTLSContext(true);
        }
    }

    public void clearSslSubjectAlternativeNameDomains() {
        sslSubjectAlternativeNameDomains.clear();
    }

    public int ringBufferSize() {
        return nextPowerOfTwo(Math.min(defaultMaxLogEntries(), 1500));
    }

    private int nextPowerOfTwo(int value) {
        for (int i = 0; i < 16; i++) {
            double powOfTwo = Math.pow(2, i);
            if (powOfTwo > value) {
                return (int) powOfTwo;
            }
        }
        return (int) Math.pow(2, 16);
    }
}
