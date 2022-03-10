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
     * </p>
     *
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
     * </p>
     *
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
     * </p>
     *
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
     * </p>
     *
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
     * <p>
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
     * <p>
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
     * <p>
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

    /**
     * <p>If true socket connections will always be closed after a response is returned, if false connection is only closed if request header indicate connection should be closed.</p>
     * <p>
     * Default is false
     *
     * @param alwaysCloseSocketConnections true socket connections will always be closed after a response is returned
     */
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

    /**
     * The local IP address to bind to for accepting new socket connections
     * <p>
     * Default is 0.0.0.0
     *
     * @param localBoundIP local IP address to bind to for accepting new socket connections
     */
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

    /**
     * Maximum size of the first line of an HTTP request
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param maxInitialLineLength maximum size of the first line of an HTTP request
     */
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

    /**
     * Maximum size of HTTP request headers
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param maxHeaderSize maximum size of HTTP request headers
     */
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

    /**
     * Maximum size of HTTP chunks in request or responses
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param maxChunkSize maximum size of HTTP chunks in request or responses
     */
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

    /**
     * If true semicolons are treated as a separator for a query parameter string, if false the semicolon is treated as a normal character that is part of a query parameter value.
     * <p>
     * The default is true
     *
     * @param useSemicolonAsQueryParameterSeparator true semicolons are treated as a separator for a query parameter string
     */
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

    /**
     * Enable CORS for MockServer REST API so that the API can be used for javascript running in browsers, such as selenium
     * <p>
     * The default is false
     *
     * @param enableCORSForAPI CORS for MockServer REST API
     */
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

    /**
     * Enable CORS for all responses from MockServer, including the REST API and expectation responses
     * <p>
     * The default is false
     *
     * @param enableCORSForAllResponses CORS for all responses from MockServer
     */
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

    /**
     * <p>Configure the default value used for CORS in the access-control-allow-headers and access-control-expose-headers headers.</p>
     * <p>In addition to this default value any headers specified in the request header access-control-request-headers also get added to access-control-allow-headers and access-control-expose-headers headers in a CORS response.</p>
     * <p>The default is "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"</p>
     *
     * @param corsAllowHeaders the default value used for CORS in the access-control-allow-headers and access-control-expose-headers headers
     */
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

    /**
     * <p>Configure the default value used for CORS in the access-control-allow-methods header.</p>
     * <p>The default is "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"</p>
     *
     * @param corsAllowMethods the default value used for CORS in the access-control-allow-methods header
     */
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

    /**
     * Configure the value used for CORS in the access-control-allow-credentials header.
     * <p>
     * The default is true
     *
     * @param corsAllowCredentials the value used for CORS in the access-control-allow-credentials header
     */
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

    /**
     * Configure the value used for CORS in the access-control-max-age header.
     * <p>
     * The default is 300
     *
     * @param corsMaxAgeInSeconds the value used for CORS in the access-control-max-age header.
     */
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

    /**
     * The class (and package) used to initialize expectations in MockServer at startup, if set MockServer will load and call this class to initialise expectations when is starts.
     * <p>
     * The default is null
     *
     * @param initializationClass class (and package) used to initialize expectations in MockServer at startup
     */
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

    /**
     * <p>The path to the json file used to initialize expectations in MockServer at startup, if set MockServer will load this file and initialise expectations for each item in the file when is starts.</p>
     * <p>The expected format of the file is a JSON array of expectations, as per the <a target="_blank" href="https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.12.x#/Expectations" target="_blank">REST API format</a></p>
     *
     * @param initializationJsonPath path to the json file used to initialize expectations in MockServer at startup
     */
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

    /**
     * <p>If enabled the initialization json file will be watched for changes, any changes found will result in expectations being created, remove or updated by matching against their key.</p>
     * <p>If duplicate keys exist only the last duplicate key in the file will be processed and all duplicates except the last duplicate will be removed.</p>
     * <p>The order of expectations in the file is the order in which they are created if they are new, however, re-ordering existing expectations does not change the order they are matched against incoming requests.</p>
     *
     * <p>The default is false</p>
     *
     * @param watchInitializationJson if enabled the initialization json file will be watched for changes
     */
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

    /**
     * Enable the persisting of expectations as json, which is updated whenever the expectation state is updated (i.e. add, clear, expires, etc)
     * <p>
     * The default is false
     *
     * @param persistExpectations the persisting of expectations as json
     */
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

    /**
     * The file path used to save persisted expectations as json, which is updated whenever the expectation state is updated (i.e. add, clear, expires, etc)
     * <p>
     * The default is "persistedExpectations.json"
     *
     * @param persistedExpectationsPath file path used to save persisted expectations as json
     */
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

    // TODO(jamesdbloom) add description and html
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

    /**
     * If true (the default) when no matching expectation is found, and the host header of the request does not match MockServer's host, then MockServer attempts to proxy the request if that fails then a 404 is returned.
     * If false when no matching expectation is found, and MockServer is not being used as a proxy, then MockServer always returns a 404 immediately.
     *
     * @param attemptToProxyIfNoMatchingExpectation enables automatically attempted proxying of request that don't match an expectation and look like they should be proxied
     */
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

    /**
     * Use HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     * <p>
     * The default is null
     *
     * @param forwardHttpProxy host and port for HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     */
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

    /**
     * Use HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests, supports TLS tunnelling of HTTPS requests
     * <p>
     * The default is null
     *
     * @param forwardHttpsProxy host and port for HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests
     */
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

    /**
     * Use SOCKS proxy for all outbound / forwarded requests, support TLS tunnelling of TCP connections
     * <p>
     * The default is null
     *
     * @param forwardSocksProxy host and port for SOCKS proxy for all outbound / forwarded requests
     */
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

    /**
     * <p>Username for proxy authentication when using HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is null
     *
     * @param forwardProxyAuthenticationUsername username for proxy authentication
     */
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

    /**
     * <p>Password for proxy authentication when using HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is null
     *
     * @param forwardProxyAuthenticationPassword password for proxy authentication
     */
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

    /**
     * The authentication realm for proxy authentication to MockServer
     *
     * @param proxyAuthenticationRealm the authentication realm for proxy authentication
     */
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

    /**
     * <p>The required username for proxy authentication to MockServer</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is ""
     *
     * @param proxyAuthenticationUsername required username for proxy authentication to MockServer
     */
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

    /**
     * <p>The required password for proxy authentication to MockServer</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is ""
     *
     * @param proxyAuthenticationPassword required password for proxy authentication to MockServer
     */
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

    /**
     * Path to support HTTP GET requests for status response (also available on PUT /mockserver/status).
     * <p>
     * If this value is not modified then only PUT /mockserver/status but is a none blank value is provided for this value then GET requests to this path will return the 200 Ok status response showing the MockServer version and bound ports.
     * <p>
     * A GET request to this path will be matched before any expectation matching or proxying of requests.
     * <p>
     * The default is ""
     *
     * @param livenessHttpGetPath path to support HTTP GET requests for status response
     */
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

    // TODO(jamesdbloom) add description and html
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

    // TODO(jamesdbloom) add description and html
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

    // TODO(jamesdbloom) add description and html
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

    // TODO(jamesdbloom) add description and html
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

    // TODO(jamesdbloom) add description and html
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

    // TODO(jamesdbloom) add description and html
    public Configuration controlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        this.controlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource;
        return this;
    }

    public Boolean proactivelyInitialiseTLS() {
        if (proactivelyInitialiseTLS == null) {
            return ConfigurationProperties.proactivelyInitialiseTLS();
        }
        return proactivelyInitialiseTLS;
    }

    /**
     * <p>Proactively initialise TLS during start to ensure that if dynamicallyCreateCertificateAuthorityCertificate is enabled the Certificate Authority X.509 Certificate and Private Key will be created during start up and not when the first TLS connection is received.</p>
     * <p>This setting will also ensure any configured private key and X.509 will be loaded during start up and not when the first TLS connection is received to give immediate feedback on any related TLS configuration errors.</p>
     *
     * @param proactivelyInitialiseTLS proactively initialise TLS at startup
     */
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

    /**
     * Enable dynamic creation of Certificate Authority X509 certificate and private key.
     * <p>
     * Enable this property to increase the security of trusting the MockServer Certificate Authority X509 by ensuring a local dynamic value is used instead of the public value in the MockServer git repo.
     * <p>
     * These PEM files will be created and saved in the directory specified with configuration property directoryToSaveDynamicSSLCertificate.
     *
     * @param dynamicallyCreateCertificateAuthorityCertificate dynamic creation of Certificate Authority X509 certificate and private key.
     */
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

    /**
     * Directory used to save the dynamically generated Certificate Authority X.509 Certificate and Private Key.
     *
     * @param directoryToSaveDynamicSSLCertificate directory to save Certificate Authority X.509 Certificate and Private Key
     */
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

    /**
     * Prevent certificates from dynamically updating when domain list changes
     *
     * @param preventCertificateDynamicUpdate prevent certificates from dynamically updating when domain list changes
     */
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

    /**
     * The domain name for auto-generate TLS certificates
     *
     * The default is "localhost"
     *
     * @param sslCertificateDomainName domain name for auto-generate TLS certificates
     */
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

    /**
     * The Subject Alternative Name (SAN) domain names for auto-generate TLS certificates
     *
     * The default is "localhost"
     *
     * @param sslSubjectAlternativeNameDomains Subject Alternative Name (SAN) domain names for auto-generate TLS certificates
     */
    public Configuration sslSubjectAlternativeNameDomains(String... sslSubjectAlternativeNameDomains) {
        this.sslSubjectAlternativeNameDomains = Sets.newConcurrentHashSet(Arrays.asList(sslSubjectAlternativeNameDomains));
        return this;
    }

    /**
     * The Subject Alternative Name (SAN) domain names for auto-generate TLS certificates
     *
     * The default is "localhost"
     *
     * @param sslSubjectAlternativeNameDomains Subject Alternative Name (SAN) domain names for auto-generate TLS certificates
     */
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

    /**
     * <p>The Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates</p>
     *
     * <p>The default is 127.0.0.1, 0.0.0.0</p>
     *
     * @param sslSubjectAlternativeNameIps Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates
     */
    public Configuration sslSubjectAlternativeNameIps(String... sslSubjectAlternativeNameIps) {
        sslSubjectAlternativeNameIps(Sets.newConcurrentHashSet(Arrays.asList(sslSubjectAlternativeNameIps)));
        return this;
    }

    /**
     * <p>The Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates</p>
     *
     * <p>The default is 127.0.0.1, 0.0.0.0</p>
     *
     * @param sslSubjectAlternativeNameIps Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates
     */
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

    /**
     * File location or classpath location of custom Private Key for Certificate Authority for TLS, the private key must be a PKCS#8 PEM file and must match the certificateAuthorityCertificate
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     *
     * @param certificateAuthorityPrivateKey location of the PEM file containing the certificate authority private key
     */
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

    /**
     * File location or classpath location of custom X.509 Certificate for Certificate Authority for TLS, the certificate must be a X509 PEM file and must match the certificateAuthorityPrivateKey
     *
     * @param certificateAuthorityCertificate location of the PEM file containing the certificate authority X509 certificate
     */
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

    /**
     * File location or classpath location of a fixed custom private key for TLS connections into MockServer.
     * <p>
     * The private key must be a PKCS#8 PEM file and must be the private key corresponding to the x509CertificatePath X509 (public key) configuration.
     * The certificateAuthorityCertificate configuration must be the Certificate Authority for the corresponding X509 certificate (i.e. able to valid its signature), see: x509CertificatePath.
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This configuration will be ignored unless x509CertificatePath is also set.
     *
     * @param privateKeyPath location of the PKCS#8 PEM file containing the private key
     */
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

    /**
     * File location or classpath location of a fixed custom X.509 Certificate for TLS connections into MockServer.
     * <p>
     * The certificate must be a X509 PEM file and must be the public key corresponding to the privateKeyPath private key configuration.
     * The certificateAuthorityCertificate configuration must be the Certificate Authority for this certificate (i.e. able to valid its signature).
     * <p>
     * This configuration will be ignored unless privateKeyPath is also set.
     *
     * @param x509CertificatePath location of the PEM file containing the X509 certificate
     */
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

    /**
     * Require mTLS (also called client authentication and two-way TLS) for all TLS connections / HTTPS requests to MockServer
     *
     * @param tlsMutualAuthenticationRequired TLS mutual authentication
     */
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

    /**
     * File location or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer performs mTLS (client authentication) for inbound TLS connections because tlsMutualAuthenticationRequired is enabled
     *
     * @param tlsMutualAuthenticationCertificateChain file location or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates
     */
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

    /**
     * Configure trusted set of certificates for forwarded or proxied requests.
     * <p>
     * MockServer will only be able to establish a TLS connection to endpoints that have a trusted X509 certificate according to the trust manager type, as follows:
     * <p>
     * <p>
     * ALL - Insecure will trust all X509 certificates and not perform host name verification.
     * JVM - Will trust all X509 certificates trust by the JVM.
     * CUSTOM - Will trust all X509 certificates specified in forwardProxyTLSCustomTrustX509Certificates configuration value.
     *
     * @param forwardProxyTLSX509CertificatesTrustManagerType trusted set of certificates for forwarded or proxied requests, allowed values: ALL, JVM, CUSTOM.
     */
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

    /**
     * File location or classpath location of custom file for trusted X509 Certificate Authority roots for forwarded or proxied requests, the certificate chain must be a X509 PEM file.
     * <p>
     * MockServer will only be able to establish a TLS connection to endpoints that have an X509 certificate chain that is signed by one of the provided custom
     * certificates, i.e. where a path can be established from the endpoints X509 certificate to one or more of the custom X509 certificates provided.
     *
     * @param forwardProxyTLSCustomTrustX509Certificates custom set of trusted X509 certificate authority roots for forwarded or proxied requests in PEM format.
     */
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

    /**
     * File location or classpath location of custom Private Key for proxied TLS connections out of MockServer, the private key must be a PKCS#8 PEM file
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This private key will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param forwardProxyPrivateKey location of the PEM file containing the private key
     */
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

    /**
     * File location or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param forwardProxyCertificateChain location of the PEM file containing the certificate chain
     */
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
