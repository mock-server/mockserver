package org.mockserver.configuration;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.memory.MemoryMonitoring;
import org.mockserver.memory.Summary;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.io.*;
import java.lang.management.MemoryType;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.logging.MockServerLogger.configureLogger;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class ConfigurationProperties {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(ConfigurationProperties.class);

    private static final String DEFAULT_LOG_LEVEL = "INFO";

    // logging
    private static final String MOCKSERVER_LOG_LEVEL = "mockserver.logLevel";
    private static final String MOCKSERVER_DISABLE_SYSTEM_OUT = "mockserver.disableSystemOut";
    private static final String MOCKSERVER_DISABLE_LOGGING = "mockserver.disableLogging";
    private static final String MOCKSERVER_DETAILED_MATCH_FAILURES = "mockserver.detailedMatchFailures";
    private static final String MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG = "mockserver.launchUIForLogLevelDebug";
    private static final String MOCKSERVER_METRICS_ENABLED = "mockserver.metricsEnabled";

    // memory usage
    private static final String MOCKSERVER_MAX_EXPECTATIONS = "mockserver.maxExpectations";
    private static final String MOCKSERVER_MAX_LOG_ENTRIES = "mockserver.maxLogEntries";
    private static final String MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS = "mockserver.maxWebSocketExpectations";
    private static final String MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV = "mockserver.outputMemoryUsageCsv";
    private static final String MOCKSERVER_MEMORY_USAGE_CSV_DIRECTORY = "mockserver.memoryUsageCsvDirectory";

    // scalability
    private static final String MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.nioEventLoopThreadCount";
    private static final String MOCKSERVER_ACTION_HANDLER_THREAD_COUNT = "mockserver.actionHandlerThreadCount";
    private static final String MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.clientNioEventLoopThreadCount";
    private static final String MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT = "mockserver.webSocketClientEventLoopThreadCount";
    private static final String MOCKSERVER_MAX_FUTURE_TIMEOUT = "mockserver.maxFutureTimeout";
    private static final String MOCKSERVER_MATCHERS_FAIL_FAST = "mockserver.matchersFailFast";

    // socket
    private static final String MOCKSERVER_MAX_SOCKET_TIMEOUT = "mockserver.maxSocketTimeout";
    private static final String MOCKSERVER_SOCKET_CONNECTION_TIMEOUT = "mockserver.socketConnectionTimeout";
    private static final String MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS = "mockserver.alwaysCloseSocketConnections";
    private static final String MOCKSERVER_LOCAL_BOUND_IP = "mockserver.localBoundIP";

    // http request parsing
    private static final String MOCKSERVER_MAX_INITIAL_LINE_LENGTH = "mockserver.maxInitialLineLength";
    private static final String MOCKSERVER_MAX_HEADER_SIZE = "mockserver.maxHeaderSize";
    private static final String MOCKSERVER_MAX_CHUNK_SIZE = "mockserver.maxChunkSize";
    private static final String MOCKSERVER_USE_SEMICOLON_AS_QUERY_PARAMETER_SEPARATOR = "mockserver.useSemicolonAsQueryParameterSeparator";

    // CORS
    private static final String MOCKSERVER_ENABLE_CORS_FOR_API = "mockserver.enableCORSForAPI";
    private static final String MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES = "mockserver.enableCORSForAllResponses";
    private static final String MOCKSERVER_CORS_ALLOW_ORIGIN = "mockserver.corsAllowOrigin";
    private static final String MOCKSERVER_CORS_ALLOW_METHODS = "mockserver.corsAllowMethods";
    private static final String MOCKSERVER_CORS_ALLOW_HEADERS = "mockserver.corsAllowHeaders";
    private static final String MOCKSERVER_CORS_ALLOW_CREDENTIALS = "mockserver.corsAllowCredentials";
    private static final String MOCKSERVER_CORS_MAX_AGE_IN_SECONDS = "mockserver.corsMaxAgeInSeconds";

    // mock initialization
    private static final String MOCKSERVER_INITIALIZATION_CLASS = "mockserver.initializationClass";
    private static final String MOCKSERVER_INITIALIZATION_JSON_PATH = "mockserver.initializationJsonPath";
    private static final String MOCKSERVER_WATCH_INITIALIZATION_JSON = "mockserver.watchInitializationJson";

    // mock persistence
    private static final String MOCKSERVER_PERSIST_EXPECTATIONS = "mockserver.persistExpectations";
    private static final String MOCKSERVER_PERSISTED_EXPECTATIONS_PATH = "mockserver.persistedExpectationsPath";

    // verification
    private static final String MOCKSERVER_MAXIMUM_NUMBER_OF_REQUESTS_TO_RETURN_IN_VERIFICATION_FAILURE = "mockserver.maximumNumberOfRequestToReturnInVerificationFailure";

    // proxy
    private static final String MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION = "mockserver.attemptToProxyIfNoMatchingExpectation";
    private static final String MOCKSERVER_FORWARD_HTTP_PROXY = "mockserver.forwardHttpProxy";
    private static final String MOCKSERVER_FORWARD_HTTPS_PROXY = "mockserver.forwardHttpsProxy";
    private static final String MOCKSERVER_FORWARD_SOCKS_PROXY = "mockserver.forwardSocksProxy";
    private static final String MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME = "mockserver.forwardProxyAuthenticationUsername";
    private static final String MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD = "mockserver.forwardProxyAuthenticationPassword";
    private static final String MOCKSERVER_PROXY_SERVER_REALM = "mockserver.proxyAuthenticationRealm";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_USERNAME = "mockserver.proxyAuthenticationUsername";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD = "mockserver.proxyAuthenticationPassword";

    // liveness
    private static final String MOCKSERVER_LIVENESS_HTTP_GET_PATH = "mockserver.livenessHttpGetPath";

    // control plane authentication
    private static final String MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_REQUIRED = "mockserver.controlPlaneTLSMutualAuthenticationRequired";
    private static final String MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN = "mockserver.controlPlaneTLSMutualAuthenticationCAChain";
    private static final String MOCKSERVER_CONTROL_PLANE_TLS_PRIVATE_KEY_PATH = "mockserver.controlPlanePrivateKeyPath";
    private static final String MOCKSERVER_CONTROL_PLANE_TLS_X509_CERTIFICATE_PATH = "mockserver.controlPlaneX509CertificatePath";
    private static final String MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED = "mockserver.controlPlaneJWTAuthenticationRequired";
    private static final String MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_JWK_SOURCE = "mockserver.controlPlaneJWTAuthenticationJWKSource";
    private static final String MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_EXPECTED_AUDIENCE = "mockserver.controlPlaneJWTAuthenticationExpectedAudience";
    private static final String MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_MATCHING_CLAIMS = "mockserver.controlPlaneJWTAuthenticationMatchingClaims";
    private static final String MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED_CLAIMS = "mockserver.controlPlaneJWTAuthenticationRequiredClaims";

    // TLS
    private static final String MOCKSERVER_PROACTIVELY_INITIALISE_TLS = "mockserver.proactivelyInitialiseTLS";

    // inbound - dynamic CA
    private static final String MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE = "mockserver.dynamicallyCreateCertificateAuthorityCertificate";
    private static final String MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE = "mockserver.directoryToSaveDynamicSSLCertificate";

    // inbound - dynamic private key & x509
    private static final String MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE = "mockserver.preventCertificateDynamicUpdate";
    private static final String MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME = "mockserver.sslCertificateDomainName";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS = "mockserver.sslSubjectAlternativeNameDomains";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS = "mockserver.sslSubjectAlternativeNameIps";

    // inbound - fixed CA
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "mockserver.certificateAuthorityPrivateKey";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "mockserver.certificateAuthorityCertificate";
    public static final String DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "org/mockserver/socket/PKCS8CertificateAuthorityPrivateKey.pem";
    public static final String DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "org/mockserver/socket/CertificateAuthorityCertificate.pem";

    // inbound - fixed private key & x509
    private static final String MOCKSERVER_TLS_PRIVATE_KEY_PATH = "mockserver.privateKeyPath";
    private static final String MOCKSERVER_TLS_X509_CERTIFICATE_PATH = "mockserver.x509CertificatePath";

    // inbound - mTLS
    private static final String MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED = "mockserver.tlsMutualAuthenticationRequired";
    private static final String MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN = "mockserver.tlsMutualAuthenticationCertificateChain";

    // outbound - CA
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE = "mockserver.forwardProxyTLSX509CertificatesTrustManagerType";

    // outbound - fixed CA
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES = "mockserver.forwardProxyTLSCustomTrustX509Certificates";

    // outbound - fixed private key & x509
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY = "mockserver.forwardProxyPrivateKey";
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN = "mockserver.forwardProxyCertificateChain";

    // properties file
    private static final String MOCKSERVER_PROPERTY_FILE = "mockserver.propertyFile";
    public static final Properties PROPERTIES = readPropertyFile();

    private static Map<String, String> slf4jOrJavaLoggerToJavaLoggerLevelMapping;

    private static Map<String, String> slf4jOrJavaLoggerToSLF4JLevelMapping;

    private static Map<String, String> getSLF4JOrJavaLoggerToJavaLoggerLevelMapping() {
        if (slf4jOrJavaLoggerToJavaLoggerLevelMapping == null) {
            slf4jOrJavaLoggerToJavaLoggerLevelMapping = ImmutableMap
                .<String, String>builder()
                .put("TRACE", "FINEST")
                .put("DEBUG", "FINE")
                .put("INFO", "INFO")
                .put("WARN", "WARNING")
                .put("ERROR", "SEVERE")
                .put("FINEST", "FINEST")
                .put("FINE", "FINE")
                .put("WARNING", "WARNING")
                .put("SEVERE", "SEVERE")
                .put("OFF", "OFF")
                .build();
        }
        return slf4jOrJavaLoggerToJavaLoggerLevelMapping;
    }

    private static Map<String, String> getSLF4JOrJavaLoggerToSLF4JLevelMapping() {
        if (slf4jOrJavaLoggerToSLF4JLevelMapping == null) {
            slf4jOrJavaLoggerToSLF4JLevelMapping = ImmutableMap
                .<String, String>builder()
                .put("FINEST", "TRACE")
                .put("FINE", "DEBUG")
                .put("INFO", "INFO")
                .put("WARNING", "WARN")
                .put("SEVERE", "ERROR")
                .put("TRACE", "TRACE")
                .put("DEBUG", "DEBUG")
                .put("WARN", "WARN")
                .put("ERROR", "ERROR")
                .put("OFF", "ERROR")
                .build();
        }
        return slf4jOrJavaLoggerToSLF4JLevelMapping;
    }

    private static String propertyFile() {
        if (isNotBlank(System.getProperty(MOCKSERVER_PROPERTY_FILE)) && System.getProperty(MOCKSERVER_PROPERTY_FILE).equals("/config/mockserver.properties")) {
            return isBlank(System.getenv("MOCKSERVER_PROPERTY_FILE")) ? System.getProperty(MOCKSERVER_PROPERTY_FILE) : System.getenv("MOCKSERVER_PROPERTY_FILE");
        } else {
            return System.getProperty(MOCKSERVER_PROPERTY_FILE, isBlank(System.getenv("MOCKSERVER_PROPERTY_FILE")) ? "mockserver.properties" : System.getenv("MOCKSERVER_PROPERTY_FILE"));
        }
    }

    // logging

    public static Level logLevel() {
        String logLevel = readPropertyHierarchically(PROPERTIES, MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase();
        if (isNotBlank(logLevel)) {
            if (getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(logLevel).equals("OFF")) {
                return null;
            } else {
                return Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(logLevel));
            }
        } else {
            return Level.INFO;
        }
    }

    public static String javaLoggerLogLevel() {
        String logLevel = readPropertyHierarchically(PROPERTIES, MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase();
        if (isNotBlank(logLevel)) {
            if (getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(logLevel).equals("OFF")) {
                return "OFF";
            } else {
                return getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(logLevel);
            }
        } else {
            return "INFO";
        }
    }

    /**
     * Override the default logging level of INFO
     *
     * @param level the log level, which can be TRACE, DEBUG, INFO, WARN, ERROR, OFF, FINEST, FINE, INFO, WARNING, SEVERE
     */
    public static void logLevel(String level) {
        if (isNotBlank(level)) {
            if (!getSLF4JOrJavaLoggerToSLF4JLevelMapping().containsKey(level)) {
                throw new IllegalArgumentException("log level \"" + level + "\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\", or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\"");
            }
            setProperty(MOCKSERVER_LOG_LEVEL, level);
        }
        configureLogger();
    }

    public static void temporaryLogLevel(String level, Runnable runnable) {
        Level originalLogLevel = logLevel();
        try {
            logLevel(level);
            runnable.run();
        } finally {
            if (originalLogLevel != null) {
                logLevel(originalLogLevel.name());
            }
        }
    }

    public static boolean disableSystemOut() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
    }

    /**
     * Disable printing log to system out for JVM, default is enabled
     *
     * @param disable printing log to system out for JVM
     */
    public static void disableSystemOut(boolean disable) {
        setProperty(MOCKSERVER_DISABLE_SYSTEM_OUT, "" + disable);
        configureLogger();
    }

    public static boolean disableLogging() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_DISABLE_LOGGING, "MOCKSERVER_DISABLE_LOGGING", "" + false));
    }

    /**
     * Disable all logging and processing of log events
     * <p>
     * The default is false
     *
     * @param disable disable all logging
     */
    public static void disableLogging(boolean disable) {
        setProperty(MOCKSERVER_DISABLE_LOGGING, "" + disable);
        configureLogger();
    }

    public static boolean detailedMatchFailures() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_DETAILED_MATCH_FAILURES, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
    }

    /**
     * If true (the default) the log event recording that a request matcher did not match will include a detailed reason why each non matching field did not match.
     *
     * @param enable enabled detailed match failure log events
     */
    public static void detailedMatchFailures(boolean enable) {
        setProperty(MOCKSERVER_DETAILED_MATCH_FAILURES, "" + enable);
    }

    public static boolean launchUIForLogLevelDebug() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG, "MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG", "" + false));
    }

    /**
     * If true (the default) the ClientAndServer constructor will open the UI in the default browser when the log level is set to DEBUG.
     *
     * @param enable enabled ClientAndServer constructor launching UI when log level is DEBUG
     */
    public static void launchUIForLogLevelDebug(boolean enable) {
        setProperty(MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG, "" + enable);
    }

    public static boolean metricsEnabled() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
    }

    /**
     * Enable gathering of metrics, default is false
     *
     * @param enable enable metrics
     */
    public static void metricsEnabled(boolean enable) {
        setProperty(MOCKSERVER_METRICS_ENABLED, "" + enable);
    }

    // memory usage

    public static long heapSizeInKB() {
        Summary heap = MemoryMonitoring.getJVMMemory(MemoryType.HEAP);
        return (heap.getNet().getMax() - heap.getNet().getUsed()) / 1024L;
    }

    public static int maxExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_EXPECTATIONS, "MOCKSERVER_MAX_EXPECTATIONS", Math.min((int) (heapSizeInKB() / 200), 5000));
    }

    /**
     * <p>
     * Maximum number of expectations stored in memory.  Expectations are stored in a circular queue so once this limit is reach the oldest and lowest priority expectations are overwritten
     * </p>
     * <p>
     * The default maximum depends on the available memory in the JVM with an upper limit of 5000
     * </p>
     *
     * @param count maximum number of expectations to store
     */
    public static void maxExpectations(int count) {
        setProperty(MOCKSERVER_MAX_EXPECTATIONS, "" + count);
    }

    public static int maxLogEntries() {
        return readIntegerProperty(MOCKSERVER_MAX_LOG_ENTRIES, "MOCKSERVER_MAX_LOG_ENTRIES", Math.min((int) (heapSizeInKB() / 75), 60000));
    }

    /**
     * <p>
     * Maximum number of log entries stored in memory.  Log entries are stored in a circular queue so once this limit is reach the oldest log entries are overwritten.
     * </p>
     * <p>
     * The default maximum depends on the available memory in the JVM with an upper limit of 60000, but can be overridden using defaultMaxLogEntries
     * </p>
     *
     * @param count maximum number of expectations to store
     */
    public static void maxLogEntries(int count) {
        setProperty(MOCKSERVER_MAX_LOG_ENTRIES, "" + count);
    }

    public static int maxWebSocketExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, "MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS", 1500);
    }

    /**
     * <p>
     * Maximum number of remote (not the same JVM) method callbacks (i.e. web sockets) registered for expectations.  The web socket client registry entries are stored in a circular queue so once this limit is reach the oldest are overwritten.
     * </p>
     * <p>
     * The default is 1500
     * </p>
     *
     * @param count maximum number of method callbacks (i.e. web sockets) registered for expectations
     */
    public static void maxWebSocketExpectations(int count) {
        setProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, "" + count);
    }

    public static boolean outputMemoryUsageCsv() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV, "MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV", "false"));
    }

    /**
     * <p>Output JVM memory usage metrics to CSV file periodically called <strong>memoryUsage_&lt;yyyy-MM-dd&gt;.csv</strong></p>
     *
     * @param enable output of JVM memory metrics
     */
    public static void outputMemoryUsageCsv(boolean enable) {
        setProperty(MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV, "" + enable);
    }

    public static String memoryUsageCsvDirectory() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_MEMORY_USAGE_CSV_DIRECTORY, "MOCKSERVER_MEMORY_USAGE_CSV_DIRECTORY", ".");
    }

    /**
     * <p>Directory to output JVM memory usage metrics CSV files to when outputMemoryUsageCsv enabled</p>
     *
     * @param directory directory to save JVM memory metrics CSV files
     */
    public static void memoryUsageCsvDirectory(String directory) {
        fileExists(directory);
        setProperty(MOCKSERVER_MEMORY_USAGE_CSV_DIRECTORY, directory);
    }

    // scalability

    public static int nioEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT", 5);
    }

    /**
     * <p>Netty worker thread pool size for handling requests and response.  These threads are used for fast non-blocking activities such as, reading and de-serialise all requests and responses.</p>
     *
     * @param count Netty worker thread pool size
     */
    public static void nioEventLoopThreadCount(int count) {
        setProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static int actionHandlerThreadCount() {
        return readIntegerProperty(MOCKSERVER_ACTION_HANDLER_THREAD_COUNT, "MOCKSERVER_ACTION_HANDLER_THREAD_COUNT", Math.max(5, Runtime.getRuntime().availableProcessors()));
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
     * @param count Netty worker thread pool size
     */
    public static void actionHandlerThreadCount(int count) {
        setProperty(MOCKSERVER_ACTION_HANDLER_THREAD_COUNT, "" + count);
    }

    public static int clientNioEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT", 5);
    }

    /**
     * <p>Client Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic.</p>
     *
     * <p>Default is 5 threads</p>
     *
     * @param count Client Netty worker thread pool size
     */
    public static void clientNioEventLoopThreadCount(int count) {
        setProperty(MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static int webSocketClientEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT", 5);
    }

    /**
     * <p>Web socket thread pool size for expectations with remote (not the same JVM) method callbacks (i.e. web sockets).</p>
     * <p>
     * Default is 5 threads
     *
     * @param count web socket worker thread pool size
     */
    public static void webSocketClientEventLoopThreadCount(int count) {
        setProperty(MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static long maxFutureTimeout() {
        return readLongProperty(MOCKSERVER_MAX_FUTURE_TIMEOUT, "MOCKSERVER_MAX_FUTURE_TIMEOUT", TimeUnit.SECONDS.toMillis(90));
    }

    /**
     * Maximum time allowed in milliseconds for any future to wait, for example when waiting for a response over a web socket callback.
     * <p>
     * Default is 60,000 ms
     *
     * @param milliseconds maximum time allowed in milliseconds
     */
    public static void maxFutureTimeout(long milliseconds) {
        setProperty(MOCKSERVER_MAX_FUTURE_TIMEOUT, "" + milliseconds);
    }

    public static boolean matchersFailFast() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_MATCHERS_FAIL_FAST, "MOCKSERVER_MATCHERS_FAIL_FAST", "" + true));
    }

    /**
     * If true (the default) request matchers will fail on the first non-matching field, if false request matchers will compare all fields.
     * This is useful to see all mismatching fields in the log event recording that a request matcher did not match.
     *
     * @param enable enabled request matchers failing fast
     */
    public static void matchersFailFast(boolean enable) {
        setProperty(MOCKSERVER_MATCHERS_FAIL_FAST, "" + enable);
    }

    // socket

    public static long maxSocketTimeout() {
        return readLongProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, "MOCKSERVER_MAX_SOCKET_TIMEOUT", TimeUnit.SECONDS.toMillis(20));
    }

    /**
     * Maximum time in milliseconds allowed for a response from a socket
     * <p>
     * Default is 20,000 ms
     *
     * @param milliseconds maximum time in milliseconds allowed
     */
    public static void maxSocketTimeout(long milliseconds) {
        setProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, "" + milliseconds);
    }

    public static long socketConnectionTimeout() {
        return readLongProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, "MOCKSERVER_SOCKET_CONNECTION_TIMEOUT", TimeUnit.SECONDS.toMillis(20));
    }

    /**
     * Maximum time in milliseconds allowed to connect to a socket
     * <p>
     * Default is 20,000 ms
     *
     * @param milliseconds maximum time allowed in milliseconds
     */
    public static void socketConnectionTimeout(long milliseconds) {
        setProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, "" + milliseconds);
    }

    /**
     * <p>If true socket connections will always be closed after a response is returned, if false connection is only closed if request header indicate connection should be closed.</p>
     * <p>
     * Default is false
     *
     * @param alwaysClose true socket connections will always be closed after a response is returned
     */
    public static void alwaysCloseSocketConnections(boolean alwaysClose) {
        setProperty(MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "" + alwaysClose);
    }

    public static boolean alwaysCloseSocketConnections() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS", "false"));
    }

    public static String localBoundIP() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_LOCAL_BOUND_IP, "MOCKSERVER_LOCAL_BOUND_IP", "");
    }

    /**
     * The local IP address to bind to for accepting new socket connections
     * <p>
     * Default is 0.0.0.0
     *
     * @param localBoundIP local IP address to bind to for accepting new socket connections
     */
    public static void localBoundIP(String localBoundIP) {
        if (isNotBlank(localBoundIP)) {
            setProperty(MOCKSERVER_LOCAL_BOUND_IP, InetAddresses.forString(localBoundIP).getHostAddress());
        }
    }

    // http request parsing

    public static int maxInitialLineLength() {
        return readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", Integer.MAX_VALUE);
    }

    /**
     * Maximum size of the first line of an HTTP request
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param length maximum size of the first line of an HTTP request
     */
    public static void maxInitialLineLength(int length) {
        setProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "" + length);
    }

    public static int maxHeaderSize() {
        return readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", Integer.MAX_VALUE);
    }

    /**
     * Maximum size of HTTP request headers
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param size maximum size of HTTP request headers
     */
    public static void maxHeaderSize(int size) {
        setProperty(MOCKSERVER_MAX_HEADER_SIZE, "" + size);
    }

    public static int maxChunkSize() {
        return readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", Integer.MAX_VALUE);
    }

    /**
     * Maximum size of HTTP chunks in request or responses
     * <p>
     * The default is Integer.MAX_VALUE
     *
     * @param size maximum size of HTTP chunks in request or responses
     */
    public static void maxChunkSize(int size) {
        setProperty(MOCKSERVER_MAX_CHUNK_SIZE, "" + size);
    }

    /**
     * If true semicolons are treated as a separator for a query parameter string, if false the semicolon is treated as a normal character that is part of a query parameter value.
     * <p>
     * The default is true
     *
     * @param useAsQueryParameterSeparator true semicolons are treated as a separator for a query parameter string
     */
    public static void useSemicolonAsQueryParameterSeparator(boolean useAsQueryParameterSeparator) {
        setProperty(MOCKSERVER_USE_SEMICOLON_AS_QUERY_PARAMETER_SEPARATOR, "" + useAsQueryParameterSeparator);
    }

    public static boolean useSemicolonAsQueryParameterSeparator() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_USE_SEMICOLON_AS_QUERY_PARAMETER_SEPARATOR, "MOCKSERVER_USE_SEMICOLON_AS_QUERY_PARAMETER_SEPARATOR", "true"));
    }

    // CORS

    public static boolean enableCORSForAPI() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", "false"));
    }

    /**
     * Enable CORS for MockServer REST API so that the API can be used for javascript running in browsers, such as selenium
     * <p>
     * The default is false
     *
     * @param enable CORS for MockServer REST API
     */
    public static void enableCORSForAPI(boolean enable) {
        setProperty(MOCKSERVER_ENABLE_CORS_FOR_API, "" + enable);
    }

    public static boolean enableCORSForAllResponses() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", "false"));
    }

    /**
     * Enable CORS for all responses from MockServer, including the REST API and expectation responses
     * <p>
     * The default is false
     *
     * @param enable CORS for all responses from MockServer
     */
    public static void enableCORSForAllResponses(boolean enable) {
        setProperty(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "" + enable);
    }

    public static String corsAllowOrigin() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CORS_ALLOW_ORIGIN, "MOCKSERVER_CORS_ALLOW_ORIGIN", "");
    }

    /**
     * <p>the value used for CORS in the access-control-allow-origin header.</p>
     * <p>The default is ""</p>
     *
     * @param corsAllowOrigin the value used for CORS in the access-control-allow-methods header
     */
    public static void corsAllowOrigin(String corsAllowOrigin) {
        setProperty(MOCKSERVER_CORS_ALLOW_ORIGIN, corsAllowOrigin);
    }

    public static String corsAllowMethods() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CORS_ALLOW_METHODS, "MOCKSERVER_CORS_ALLOW_METHODS", "");
    }

    /**
     * <p>The value used for CORS in the access-control-allow-methods header.</p>
     * <p>The default is "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"</p>
     *
     * @param corsAllowMethods the value used for CORS in the access-control-allow-methods header
     */
    public static void corsAllowMethods(String corsAllowMethods) {
        setProperty(MOCKSERVER_CORS_ALLOW_METHODS, corsAllowMethods);
    }

    public static String corsAllowHeaders() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CORS_ALLOW_HEADERS, "MOCKSERVER_CORS_ALLOW_HEADERS", "");
    }

    /**
     * <p>the value used for CORS in the access-control-allow-headers and access-control-expose-headers headers.</p>
     * <p>In addition to this default value any headers specified in the request header access-control-request-headers also get added to access-control-allow-headers and access-control-expose-headers headers in a CORS response.</p>
     * <p>The default is "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"</p>
     *
     * @param corsAllowHeaders the value used for CORS in the access-control-allow-headers and access-control-expose-headers headers
     */
    public static void corsAllowHeaders(String corsAllowHeaders) {
        setProperty(MOCKSERVER_CORS_ALLOW_HEADERS, corsAllowHeaders);
    }

    public static boolean corsAllowCredentials() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_CORS_ALLOW_CREDENTIALS, "MOCKSERVER_CORS_ALLOW_CREDENTIALS", "false"));
    }

    /**
     * The value used for CORS in the access-control-allow-credentials header.
     * <p>
     * The default is true
     *
     * @param allow the value used for CORS in the access-control-allow-credentials header
     */
    public static void corsAllowCredentials(boolean allow) {
        setProperty(MOCKSERVER_CORS_ALLOW_CREDENTIALS, "" + allow);
    }

    public static int corsMaxAgeInSeconds() {
        return readIntegerProperty(MOCKSERVER_CORS_MAX_AGE_IN_SECONDS, "MOCKSERVER_CORS_MAX_AGE_IN_SECONDS", 0);
    }

    /**
     * The value used for CORS in the access-control-max-age header.
     * <p>
     * The default is 300
     *
     * @param ageInSeconds the value used for CORS in the access-control-max-age header.
     */
    public static void corsMaxAgeInSeconds(int ageInSeconds) {
        setProperty(MOCKSERVER_CORS_MAX_AGE_IN_SECONDS, "" + ageInSeconds);
    }

    // mock initialization

    public static String initializationClass() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_INITIALIZATION_CLASS, "MOCKSERVER_INITIALIZATION_CLASS", "");
    }

    /**
     * The class (and package) used to initialize expectations in MockServer at startup, if set MockServer will load and call this class to initialise expectations when is starts.
     * <p>
     * The default is null
     *
     * @param initializationClass class (and package) used to initialize expectations in MockServer at startup
     */
    public static void initializationClass(String initializationClass) {
        setProperty(MOCKSERVER_INITIALIZATION_CLASS, initializationClass);
    }

    public static String initializationJsonPath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_INITIALIZATION_JSON_PATH, "MOCKSERVER_INITIALIZATION_JSON_PATH", "");
    }

    /**
     * <p>The path to the json file used to initialize expectations in MockServer at startup, if set MockServer will load this file and initialise expectations for each item in the file when is starts.</p>
     * <p>The expected format of the file is a JSON array of expectations, as per the <a target="_blank" href="https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.13.x#/Expectations" target="_blank">REST API format</a></p>
     *
     * @param initializationJsonPath path to the json file used to initialize expectations in MockServer at startup
     */
    public static void initializationJsonPath(String initializationJsonPath) {
        setProperty(MOCKSERVER_INITIALIZATION_JSON_PATH, initializationJsonPath);
    }

    public static boolean watchInitializationJson() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_WATCH_INITIALIZATION_JSON, "MOCKSERVER_WATCH_INITIALIZATION_JSON", "" + false));
    }

    /**
     * <p>If enabled the initialization json file will be watched for changes, any changes found will result in expectations being created, remove or updated by matching against their key.</p>
     * <p>If duplicate keys exist only the last duplicate key in the file will be processed and all duplicates except the last duplicate will be removed.</p>
     * <p>The order of expectations in the file is the order in which they are created if they are new, however, re-ordering existing expectations does not change the order they are matched against incoming requests.</p>
     *
     * <p>The default is false</p>
     *
     * @param enable if enabled the initialization json file will be watched for changes
     */
    public static void watchInitializationJson(boolean enable) {
        setProperty(MOCKSERVER_WATCH_INITIALIZATION_JSON, "" + enable);
    }

    // mock persistence

    public static boolean persistExpectations() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_PERSIST_EXPECTATIONS, "MOCKSERVER_PERSIST_EXPECTATIONS", "" + false));
    }

    /**
     * Enable the persisting of expectations as json, which is updated whenever the expectation state is updated (i.e. add, clear, expires, etc)
     * <p>
     * The default is false
     *
     * @param enable the persisting of expectations as json
     */
    public static void persistExpectations(boolean enable) {
        setProperty(MOCKSERVER_PERSIST_EXPECTATIONS, "" + enable);
    }

    public static String persistedExpectationsPath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_PERSISTED_EXPECTATIONS_PATH, "MOCKSERVER_PERSISTED_EXPECTATIONS_PATH", "persistedExpectations.json");
    }

    /**
     * The file path used to save persisted expectations as json, which is updated whenever the expectation state is updated (i.e. add, clear, expires, etc)
     * <p>
     * The default is "persistedExpectations.json"
     *
     * @param persistedExpectationsPath file path used to save persisted expectations as json
     */
    public static void persistedExpectationsPath(String persistedExpectationsPath) {
        setProperty(MOCKSERVER_PERSISTED_EXPECTATIONS_PATH, persistedExpectationsPath);
    }

    // verification

    public static Integer maximumNumberOfRequestToReturnInVerificationFailure() {
        return readIntegerProperty(MOCKSERVER_MAXIMUM_NUMBER_OF_REQUESTS_TO_RETURN_IN_VERIFICATION_FAILURE, "MOCKSERVER_MAXIMUM_NUMBER_OF_REQUESTS_TO_RETURN_IN_VERIFICATION_FAILURE", 10);
    }

    /**
     * The maximum number of requests to return in verification failure result, if more expectations are found the failure result does not list them separately
     *
     * @param maximumNumberOfRequestToReturnInVerification maximum number of expectations to return in verification failure result
     */
    public static void maximumNumberOfRequestToReturnInVerificationFailure(Integer maximumNumberOfRequestToReturnInVerification) {
        setProperty(MOCKSERVER_MAXIMUM_NUMBER_OF_REQUESTS_TO_RETURN_IN_VERIFICATION_FAILURE, "" + maximumNumberOfRequestToReturnInVerification);
    }

    // proxy

    public static boolean attemptToProxyIfNoMatchingExpectation() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION", "" + true));
    }

    /**
     * If true (the default) when no matching expectation is found, and the host header of the request does not match MockServer's host, then MockServer attempts to proxy the request if that fails then a 404 is returned.
     * If false when no matching expectation is found, and MockServer is not being used as a proxy, then MockServer always returns a 404 immediately.
     *
     * @param enable enables automatically attempted proxying of request that don't match an expectation and look like they should be proxied
     */
    public static void attemptToProxyIfNoMatchingExpectation(boolean enable) {
        setProperty(MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "" + enable);
    }

    public static InetSocketAddress forwardHttpProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_HTTP_PROXY, "MOCKSERVER_FORWARD_HTTP_PROXY");
    }

    /**
     * Use HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     */
    public static void forwardHttpProxy(String hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort, MOCKSERVER_FORWARD_HTTP_PROXY);
    }

    /**
     * Use HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for HTTP proxy (i.e. via Host header) for all outbound / forwarded requests
     */
    public static void forwardHttpProxy(InetSocketAddress hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort.toString(), MOCKSERVER_FORWARD_HTTP_PROXY);
    }

    public static InetSocketAddress forwardHttpsProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_HTTPS_PROXY, "MOCKSERVER_FORWARD_HTTPS_PROXY");
    }

    /**
     * Use HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests, supports TLS tunnelling of HTTPS requests
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests
     */
    public static void forwardHttpsProxy(String hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort, MOCKSERVER_FORWARD_HTTPS_PROXY);
    }

    /**
     * Use HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests, supports TLS tunnelling of HTTPS requests
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests
     */
    public static void forwardHttpsProxy(InetSocketAddress hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort.toString(), MOCKSERVER_FORWARD_HTTPS_PROXY);
    }

    public static InetSocketAddress forwardSocksProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_SOCKS_PROXY, "MOCKSERVER_FORWARD_SOCKS_PROXY");
    }

    /**
     * Use SOCKS proxy for all outbound / forwarded requests, support TLS tunnelling of TCP connections
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for SOCKS proxy for all outbound / forwarded requests
     */
    public static void forwardSocksProxy(String hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort, MOCKSERVER_FORWARD_SOCKS_PROXY);
    }

    /**
     * Use SOCKS proxy for all outbound / forwarded requests, support TLS tunnelling of TCP connections
     * <p>
     * The default is null
     *
     * @param hostAndPort host and port for SOCKS proxy for all outbound / forwarded requests
     */
    public static void forwardSocksProxy(InetSocketAddress hostAndPort) {
        validateHostAndPortAndSetProperty(hostAndPort.toString(), MOCKSERVER_FORWARD_SOCKS_PROXY);
    }

    public static String forwardProxyAuthenticationUsername() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME, "MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME", "");
    }

    /**
     * <p>Username for proxy authentication when using HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is null
     *
     * @param forwardProxyAuthenticationUsername username for proxy authentication
     */
    public static void forwardProxyAuthenticationUsername(String forwardProxyAuthenticationUsername) {
        if (forwardProxyAuthenticationUsername != null) {
            setProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME, forwardProxyAuthenticationUsername);
        } else {
            clearProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME);
        }
    }

    public static String forwardProxyAuthenticationPassword() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD, "MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD", "");
    }

    /**
     * <p>Password for proxy authentication when using HTTPS proxy (i.e. HTTP CONNECT) for all outbound / forwarded requests</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is null
     *
     * @param forwardProxyAuthenticationPassword password for proxy authentication
     */
    public static void forwardProxyAuthenticationPassword(String forwardProxyAuthenticationPassword) {
        if (forwardProxyAuthenticationPassword != null) {
            setProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD, forwardProxyAuthenticationPassword);
        } else {
            clearProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD);
        }
    }

    public static String proxyAuthenticationRealm() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_PROXY_SERVER_REALM, "MOCKSERVER_PROXY_SERVER_REALM", "MockServer HTTP Proxy");
    }

    /**
     * The authentication realm for proxy authentication to MockServer
     *
     * @param proxyAuthenticationRealm the authentication realm for proxy authentication
     */
    public static void proxyAuthenticationRealm(String proxyAuthenticationRealm) {
        setProperty(MOCKSERVER_PROXY_SERVER_REALM, proxyAuthenticationRealm);
    }

    public static String proxyAuthenticationUsername() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, "MOCKSERVER_PROXY_AUTHENTICATION_USERNAME", "");
    }

    /**
     * <p>The required username for proxy authentication to MockServer</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is ""
     *
     * @param proxyAuthenticationUsername required username for proxy authentication to MockServer
     */
    public static void proxyAuthenticationUsername(String proxyAuthenticationUsername) {
        setProperty(MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, proxyAuthenticationUsername);
    }

    public static String proxyAuthenticationPassword() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, "MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD", "");
    }

    /**
     * <p>The required password for proxy authentication to MockServer</p>
     * <p><strong>Note:</strong> <a target="_blank" href="https://www.oracle.com/java/technologies/javase/8u111-relnotes.html">8u111 Update Release Notes</a> state that the Basic authentication scheme has been deactivated when setting up an HTTPS tunnel.  To resolve this clear or set to an empty string the following system properties: <code class="inline code">jdk.http.auth.tunneling.disabledSchemes</code> and <code class="inline code">jdk.http.auth.proxying.disabledSchemes</code>.</p>
     * <p>
     * The default is ""
     *
     * @param proxyAuthenticationPassword required password for proxy authentication to MockServer
     */
    public static void proxyAuthenticationPassword(String proxyAuthenticationPassword) {
        setProperty(MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, proxyAuthenticationPassword);
    }

    // liveness

    public static String livenessHttpGetPath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_LIVENESS_HTTP_GET_PATH, "MOCKSERVER_LIVENESS_HTTP_GET_PATH", "");
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
     * @param livenessPath path to support HTTP GET requests for status response
     */
    public static void livenessHttpGetPath(String livenessPath) {
        setProperty(MOCKSERVER_LIVENESS_HTTP_GET_PATH, livenessPath);
    }

    // control plane authentication

    public static boolean controlPlaneTLSMutualAuthenticationRequired() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_REQUIRED", "false"));
    }

    /**
     * Require mTLS (also called client authentication and two-way TLS) for all control plane requests
     *
     * @param enable TLS mutual authentication for all control plane requests
     */
    public static void controlPlaneTLSMutualAuthenticationRequired(boolean enable) {
        setProperty(MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "" + enable);
    }

    public static String controlPlaneTLSMutualAuthenticationCAChain() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", "");
    }

    /**
     * File system path or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for control plane mTLS authentication
     * <p>
     * The X.509 Certificate Chain is for trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used for to performs mTLS (client authentication) for inbound TLS connections if controlPlaneTLSMutualAuthenticationRequired is enabled
     *
     * @param trustCertificateChain File system path or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates
     */
    public static void controlPlaneTLSMutualAuthenticationCAChain(String trustCertificateChain) {
        setProperty(MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "" + trustCertificateChain);
    }

    public static String controlPlanePrivateKeyPath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_TLS_PRIVATE_KEY_PATH, "MOCKSERVER_CONTROL_PLANE_TLS_PRIVATE_KEY_PATH", "");
    }

    /**
     * File system path or classpath location of a fixed custom private key for control plane connections using mTLS for authentication.
     * <p>
     * The private key must be a PKCS#8 or PKCS#1 PEM file and must be the private key corresponding to the controlPlaneX509CertificatePath X509 (public key) configuration.
     * The controlPlaneTLSMutualAuthenticationCAChain configuration must be the Certificate Authority for the corresponding X509 certificate (i.e. able to valid its signature).
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This configuration will be ignored unless x509CertificatePath is also set.
     *
     * @param privateKeyPath location of the PKCS#8 PEM file containing the private key
     */
    public static void controlPlanePrivateKeyPath(String privateKeyPath) {
        fileExists(privateKeyPath);
        setProperty(MOCKSERVER_CONTROL_PLANE_TLS_PRIVATE_KEY_PATH, privateKeyPath);
    }


    public static String controlPlaneX509CertificatePath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_TLS_X509_CERTIFICATE_PATH, "MOCKSERVER_CONTROL_PLANE_TLS_X509_CERTIFICATE_PATH", "");
    }

    /**
     * File system path or classpath location of a fixed custom X.509 Certificate for control plane connections using mTLS for authentication.
     * <p>
     * The certificate must be a X509 PEM file and must be the public key corresponding to the controlPlanePrivateKeyPath private key configuration.
     * The controlPlaneTLSMutualAuthenticationCAChain configuration must be the Certificate Authority for this certificate (i.e. able to valid its signature).
     * <p>
     * This configuration will be ignored unless privateKeyPath is also set.
     *
     * @param x509CertificatePath location of the PEM file containing the X509 certificate
     */
    public static void controlPlaneX509CertificatePath(String x509CertificatePath) {
        fileExists(x509CertificatePath);
        setProperty(MOCKSERVER_CONTROL_PLANE_TLS_X509_CERTIFICATE_PATH, x509CertificatePath);
    }

    public static boolean controlPlaneJWTAuthenticationRequired() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED, "MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED", "false"));
    }

    /**
     * <p>
     * Require JWT authentication for all control plane requests
     * </p>
     *
     * @param enable TLS mutual authentication for all control plane requests
     */
    public static void controlPlaneJWTAuthenticationRequired(boolean enable) {
        setProperty(MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED, "" + enable);
    }

    public static String controlPlaneJWTAuthenticationJWKSource() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_JWK_SOURCE, "MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_JWK_SOURCE", "");
    }

    /**
     * <p>
     * JWK source used when JWT authentication is enabled for control plane requests
     * </p>
     * <p>
     * JWK source can be a file system path, classpath location or a URL
     * </p>
     * <p>
     * See: https://openid.net/specs/draft-jones-json-web-key-03.html
     * </p>
     *
     * @param controlPlaneJWTAuthenticationJWKSource file system path, classpath location or a URL of JWK source
     */
    public static void controlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        setProperty(MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_JWK_SOURCE, "" + controlPlaneJWTAuthenticationJWKSource);
    }

    public static String controlPlaneJWTAuthenticationExpectedAudience() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_EXPECTED_AUDIENCE, "MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_EXPECTED_AUDIENCE", "");
    }

    /**
     * <p>
     * Audience claim (i.e. aud) required when JWT authentication is enabled for control plane requests
     * </p>
     *
     * @param controlPlaneJWTAuthenticationExpectedAudience required value for audience claim (i.e. aud)
     */
    public static void controlPlaneJWTAuthenticationExpectedAudience(String controlPlaneJWTAuthenticationExpectedAudience) {
        setProperty(MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_EXPECTED_AUDIENCE, "" + controlPlaneJWTAuthenticationExpectedAudience);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static Map<String, String> controlPlaneJWTAuthenticationMatchingClaims() {
        String jwtAuthenticationMatchingClaims = readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_MATCHING_CLAIMS, "MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_MATCHING_CLAIMS", "");
        if (isNotBlank(jwtAuthenticationMatchingClaims)) {
            return Splitter.on(",").withKeyValueSeparator("=").split(jwtAuthenticationMatchingClaims);
        } else {
            return ImmutableMap.of();
        }
    }

    /**
     * <p>
     * Matching claims expected when JWT authentication is enabled for control plane requests
     * </p>
     * <p>
     * Value should be string with comma separated key=value items, for example: scope=internal public,sub=some_subject
     * </p>
     *
     * @param controlPlaneJWTAuthenticationMatchingClaims required values for claims
     */
    public static void controlPlaneJWTAuthenticationMatchingClaims(Map<String, String> controlPlaneJWTAuthenticationMatchingClaims) {
        setProperty(MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_MATCHING_CLAIMS, Joiner.on(",").withKeyValueSeparator("=").join(controlPlaneJWTAuthenticationMatchingClaims));
    }

    public static Set<String> controlPlaneJWTAuthenticationRequiredClaims() {
        String jwtAuthenticationRequiredClaims = readPropertyHierarchically(PROPERTIES, MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED_CLAIMS, "MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED_CLAIMS", "");
        if (isNotBlank(jwtAuthenticationRequiredClaims)) {
            return Sets.newConcurrentHashSet(Arrays.asList(jwtAuthenticationRequiredClaims.split(",")));
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * <p>
     * Required claims that should exist (i.e. with any value) when JWT authentication is enabled for control plane requests
     * </p>
     * <p>
     * Value should be string with comma separated values, for example: scope,sub
     * </p>
     *
     * @param controlPlaneJWTAuthenticationRequiredClaims required claims
     */
    public static void controlPlaneJWTAuthenticationRequiredClaims(Set<String> controlPlaneJWTAuthenticationRequiredClaims) {
        setProperty(MOCKSERVER_CONTROL_PLANE_JWT_AUTHENTICATION_REQUIRED_CLAIMS, Joiner.on(",").join(controlPlaneJWTAuthenticationRequiredClaims));
    }

    // TLS

    /**
     * <p>Proactively initialise TLS during start to ensure that if dynamicallyCreateCertificateAuthorityCertificate is enabled the Certificate Authority X.509 Certificate and Private Key will be created during start up and not when the first TLS connection is received.</p>
     * <p>This setting will also ensure any configured private key and X.509 will be loaded during start up and not when the first TLS connection is received to give immediate feedback on any related TLS configuration errors.</p>
     *
     * @param enable proactively initialise TLS at startup
     */
    public static void proactivelyInitialiseTLS(boolean enable) {
        setProperty(MOCKSERVER_PROACTIVELY_INITIALISE_TLS, "" + enable);
    }

    public static boolean proactivelyInitialiseTLS() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_PROACTIVELY_INITIALISE_TLS, "MOCKSERVER_PROACTIVELY_INITIALISE_TLS", "false"));
    }

    public static boolean dynamicallyCreateCertificateAuthorityCertificate() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE, "MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE", "false"));
    }

    /**
     * Enable dynamic creation of Certificate Authority X509 certificate and private key.
     * <p>
     * Enable this property to increase the security of trusting the MockServer Certificate Authority X509 by ensuring a local dynamic value is used instead of the public value in the MockServer git repo.
     * <p>
     * These PEM files will be created and saved in the directory specified with configuration property directoryToSaveDynamicSSLCertificate.
     *
     * @param enable dynamic creation of Certificate Authority X509 certificate and private key.
     */
    public static void dynamicallyCreateCertificateAuthorityCertificate(boolean enable) {
        setProperty(MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE, "" + enable);
    }

    public static String directoryToSaveDynamicSSLCertificate() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, "MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE", ".");
    }

    /**
     * Directory used to save the dynamically generated Certificate Authority X.509 Certificate and Private Key.
     *
     * @param directoryToSaveDynamicSSLCertificate directory to save Certificate Authority X.509 Certificate and Private Key
     */
    public static void directoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        fileExists(directoryToSaveDynamicSSLCertificate);
        setProperty(MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, directoryToSaveDynamicSSLCertificate);
    }

    /**
     * Prevent certificates from dynamically updating when domain list changes
     *
     * @param prevent prevent certificates from dynamically updating when domain list changes
     */
    public static void preventCertificateDynamicUpdate(boolean prevent) {
        setProperty(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "" + prevent);
    }

    public static boolean preventCertificateDynamicUpdate() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", "false"));
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, "MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME", KeyAndCertificateFactory.CERTIFICATE_DOMAIN);
    }

    /**
     * The domain name for auto-generate TLS certificates
     * <p>
     * The default is "localhost"
     *
     * @param domainName domain name for auto-generate TLS certificates
     */
    public static void sslCertificateDomainName(String domainName) {
        setProperty(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, domainName);
    }

    /**
     * The Subject Alternative Name (SAN) domain names for auto-generate TLS certificates as a comma separated list
     * <p>
     * The default is "localhost"
     *
     * @param sslSubjectAlternativeNameDomains Subject Alternative Name (SAN) domain names for auto-generate TLS certificates
     */
    public static void sslSubjectAlternativeNameDomains(Set<String> sslSubjectAlternativeNameDomains) {
        setProperty(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS, Joiner.on(",").join(sslSubjectAlternativeNameDomains));
    }

    public static Set<String> sslSubjectAlternativeNameDomains() {
        return Sets.newConcurrentHashSet(Arrays.asList(readPropertyHierarchically(PROPERTIES, MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS", "localhost").split(",")));
    }

    /**
     * <p>The Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates as a comma separated list</p>
     *
     * <p>The default is "127.0.0.1,0.0.0.0"</p>
     *
     * @param sslSubjectAlternativeNameIps Subject Alternative Name (SAN) IP addresses for auto-generate TLS certificates
     */
    public static void sslSubjectAlternativeNameIps(Set<String> sslSubjectAlternativeNameIps) {
        setProperty(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, Joiner.on(",").join(sslSubjectAlternativeNameIps));
    }

    public static Set<String> sslSubjectAlternativeNameIps() {
        return Sets.newConcurrentHashSet(Arrays.asList(readPropertyHierarchically(PROPERTIES, MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS", "127.0.0.1,0.0.0.0").split(",")));
    }

    public static String certificateAuthorityPrivateKey() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY, "MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY", DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
    }

    /**
     * File system path or classpath location of custom Private Key for Certificate Authority for TLS, the private key must be a PKCS#8 or PKCS#1 PEM file and must match the certificateAuthorityCertificate
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     *
     * @param certificateAuthorityPrivateKey location of the PEM file containing the certificate authority private key
     */
    public static void certificateAuthorityPrivateKey(String certificateAuthorityPrivateKey) {
        setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY, certificateAuthorityPrivateKey);
    }

    public static String certificateAuthorityCertificate() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, "MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE", DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
    }

    /**
     * File system path or classpath location of custom X.509 Certificate for Certificate Authority for TLS, the certificate must be a X509 PEM file and must match the certificateAuthorityPrivateKey
     *
     * @param certificateAuthorityCertificate location of the PEM file containing the certificate authority X509 certificate
     */
    public static void certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        fileExists(certificateAuthorityCertificate);
        setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, certificateAuthorityCertificate);
    }

    public static String privateKeyPath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_TLS_PRIVATE_KEY_PATH, "MOCKSERVER_TLS_PRIVATE_KEY_PATH", "");
    }

    /**
     * File system path or classpath location of a fixed custom private key for TLS connections into MockServer.
     * <p>
     * The private key must be a PKCS#8 or PKCS#1 PEM file and must be the private key corresponding to the x509CertificatePath X509 (public key) configuration.
     * The certificateAuthorityCertificate configuration must be the Certificate Authority for the corresponding X509 certificate (i.e. able to valid its signature), see: x509CertificatePath.
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This configuration will be ignored unless x509CertificatePath is also set.
     *
     * @param privateKeyPath location of the PKCS#8 PEM file containing the private key
     */
    public static void privateKeyPath(String privateKeyPath) {
        fileExists(privateKeyPath);
        setProperty(MOCKSERVER_TLS_PRIVATE_KEY_PATH, privateKeyPath);
    }


    public static String x509CertificatePath() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_TLS_X509_CERTIFICATE_PATH, "MOCKSERVER_TLS_X509_CERTIFICATE_PATH", "");
    }

    /**
     * File system path or classpath location of a fixed custom X.509 Certificate for TLS connections into MockServer.
     * <p>
     * The certificate must be a X509 PEM file and must be the public key corresponding to the privateKeyPath private key configuration.
     * The certificateAuthorityCertificate configuration must be the Certificate Authority for this certificate (i.e. able to valid its signature).
     * <p>
     * This configuration will be ignored unless privateKeyPath is also set.
     *
     * @param x509CertificatePath location of the PEM file containing the X509 certificate
     */
    public static void x509CertificatePath(String x509CertificatePath) {
        fileExists(x509CertificatePath);
        setProperty(MOCKSERVER_TLS_X509_CERTIFICATE_PATH, x509CertificatePath);
    }

    public static boolean tlsMutualAuthenticationRequired() {
        return Boolean.parseBoolean(readPropertyHierarchically(PROPERTIES, MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED", "false"));
    }

    /**
     * Require mTLS (also called client authentication and two-way TLS) for all TLS connections / HTTPS requests to MockServer
     *
     * @param enable TLS mutual authentication
     */
    public static void tlsMutualAuthenticationRequired(boolean enable) {
        setProperty(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "" + enable);
    }

    public static String tlsMutualAuthenticationCertificateChain() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", "");
    }

    /**
     * File system path or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer performs mTLS (client authentication) for inbound TLS connections because tlsMutualAuthenticationRequired is enabled
     *
     * @param trustCertificateChain File system path or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates
     */
    public static void tlsMutualAuthenticationCertificateChain(String trustCertificateChain) {
        setProperty(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "" + trustCertificateChain);
    }

    public static ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType() {
        String forwardProxyTlsX509CertificatesTrustManagerType = readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE", "ANY");
        try {
            return ForwardProxyTLSX509CertificatesTrustManager.valueOf(forwardProxyTlsX509CertificatesTrustManagerType);
        } catch (Throwable ignore) {
            throw new IllegalArgumentException("Invalid value for ForwardProxyTLSX509CertificatesTrustManager \"" + forwardProxyTlsX509CertificatesTrustManagerType + "\" the only supported values are: " + Arrays.stream(ForwardProxyTLSX509CertificatesTrustManager.values()).map(Enum::name).collect(Collectors.toList()));
        }
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
     * @param trustManagerType trusted set of certificates for forwarded or proxied requests, allowed values: ALL, JVM, CUSTOM.
     */
    public static void forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager trustManagerType) {
        setProperty(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, trustManagerType.name());
    }

    public static String forwardProxyTLSCustomTrustX509Certificates() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, "MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES", "");
    }

    /**
     * File system path or classpath location of custom file for trusted X509 Certificate Authority roots for forwarded or proxied requests, the certificate chain must be a X509 PEM file.
     * <p>
     * MockServer will only be able to establish a TLS connection to endpoints that have an X509 certificate chain that is signed by one of the provided custom
     * certificates, i.e. where a path can be established from the endpoints X509 certificate to one or more of the custom X509 certificates provided.
     *
     * @param customX509Certificates custom set of trusted X509 certificate authority roots for forwarded or proxied requests in PEM format.
     */
    public static void forwardProxyTLSCustomTrustX509Certificates(String customX509Certificates) {
        fileExists(customX509Certificates);
        setProperty(MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, customX509Certificates);
    }

    public static String forwardProxyPrivateKey() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, "MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY", "");
    }

    /**
     * File system path or classpath location of custom Private Key for proxied TLS connections out of MockServer, the private key must be a PKCS#8 or PKCS#1 PEM file
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This private key will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param privateKey location of the PEM file containing the private key
     */
    public static void forwardProxyPrivateKey(String privateKey) {
        fileExists(privateKey);
        setProperty(MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, privateKey);
    }

    public static String forwardProxyCertificateChain() {
        return readPropertyHierarchically(PROPERTIES, MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN", "");
    }

    /**
     * File system path or classpath location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param certificateChain location of the PEM file containing the certificate chain
     */
    public static void forwardProxyCertificateChain(String certificateChain) {
        fileExists(certificateChain);
        setProperty(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, certificateChain);
    }

    @SuppressWarnings("ConstantConditions")
    static void fileExists(String file) {
        try {
            if (isNotBlank(file) && FileReader.openStreamToFileFromClassPathOrPath(file) == null) {
                throw new RuntimeException(file + " does not exist or is not accessible");
            }
        } catch (FileNotFoundException e) {
            if (!new File(file).exists()) {
                throw new RuntimeException(file + " does not exist or is not accessible");
            }
        }
    }

    private static void validateHostAndPortAndSetProperty(String hostAndPort, String mockserverSocksProxy) {
        if (isNotBlank(hostAndPort)) {
            if (hostAndPort.startsWith("/")) {
                hostAndPort = StringUtils.substringAfter(hostAndPort, "/");
            }
            String errorMessage = "Invalid property value \"" + hostAndPort + "\" for \"" + mockserverSocksProxy + "\" must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\"";
            try {
                URI uri = new URI("https://" + hostAndPort);
                if (uri.getHost() == null || uri.getPort() == -1) {
                    throw new IllegalArgumentException(errorMessage);
                } else {
                    setProperty(mockserverSocksProxy, hostAndPort);
                }
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(errorMessage);
            }
        } else {
            clearProperty(mockserverSocksProxy);
        }
    }

    private static InetSocketAddress readInetSocketAddressProperty(String key, String environmentVariableKey) {
        InetSocketAddress inetSocketAddress = null;
        String proxy = readPropertyHierarchically(PROPERTIES, key, environmentVariableKey, "");
        if (isNotBlank(proxy)) {
            String[] proxyParts = proxy.split(":");
            if (proxyParts.length > 1) {
                try {
                    inetSocketAddress = new InetSocketAddress(proxyParts[0], Integer.parseInt(proxyParts[1]));
                } catch (NumberFormatException nfe) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("NumberFormatException converting value \"" + proxyParts[1] + "\" into an integer")
                            .setThrowable(nfe)
                    );
                }
            }
        }
        return inetSocketAddress;
    }

    private static Integer readIntegerProperty(String key, String environmentVariableKey, int defaultValue) {
        try {
            return Integer.parseInt(readPropertyHierarchically(PROPERTIES, key, environmentVariableKey, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(PROPERTIES, key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, String environmentVariableKey, long defaultValue) {
        try {
            return Long.parseLong(readPropertyHierarchically(PROPERTIES, key, environmentVariableKey, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(PROPERTIES, key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return defaultValue;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static Properties readPropertyFile() {

        Properties properties = new Properties();

        try (InputStream inputStream = ConfigurationProperties.class.getClassLoader().getResourceAsStream(propertyFile())) {
            if (inputStream != null) {
                try {
                    properties.load(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                    if (MOCK_SERVER_LOGGER != null) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setAlwaysLog(true)
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("exception loading property file [" + propertyFile() + "]")
                                .setThrowable(e)
                        );
                    }
                }
            } else {
                if (MOCK_SERVER_LOGGER != null && MockServerLogger.isEnabled(DEBUG)) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(DEBUG)
                            .setMessageFormat("property file not found on classpath using path [" + propertyFile() + "]")
                    );
                }
                try {
                    properties.load(new FileInputStream(propertyFile()));
                } catch (FileNotFoundException e) {
                    if (MOCK_SERVER_LOGGER != null && MockServerLogger.isEnabled(DEBUG)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setType(SERVER_CONFIGURATION)
                                .setLogLevel(DEBUG)
                                .setMessageFormat("property file not found using path [" + propertyFile() + "]")
                        );
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (MOCK_SERVER_LOGGER != null) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setAlwaysLog(true)
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("exception loading property file [" + propertyFile() + "]")
                                .setThrowable(e)
                        );
                    }
                }
            }
        } catch (IOException ioe) {
            // ignore
        }

        if (!properties.isEmpty()) {
            Enumeration<?> propertyNames = properties.propertyNames();

            StringBuilder propertiesLogDump = new StringBuilder();
            propertiesLogDump.append("Reading properties from property file [").append(propertyFile()).append("]:").append(NEW_LINE);
            while (propertyNames.hasMoreElements()) {
                String propertyName = String.valueOf(propertyNames.nextElement());
                propertiesLogDump.append("  ").append(propertyName).append(" = ").append(properties.getProperty(propertyName)).append(NEW_LINE);
            }

            Level logLevel = Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(properties, MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()));
            if (MOCK_SERVER_LOGGER != null && MockServerLogger.isEnabled(Level.INFO, logLevel)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setAlwaysLog(true)
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(Level.INFO)
                        .setMessageFormat(propertiesLogDump.toString())
                );
            }
        }

        return properties;
    }

    private static Map<String, String> propertyCache;

    private static Map<String, String> getPropertyCache() {
        if (propertyCache == null) {
            propertyCache = new ConcurrentHashMap<>();
        }
        return propertyCache;
    }

    private static void setProperty(String systemPropertyKey, String value) {
        getPropertyCache().put(systemPropertyKey, value);
        System.setProperty(systemPropertyKey, value);
    }

    private static void clearProperty(String systemPropertyKey) {
        getPropertyCache().remove(systemPropertyKey);
        System.clearProperty(systemPropertyKey);
    }

    private static String readPropertyHierarchically(Properties properties, String systemPropertyKey, String environmentVariableKey, String defaultValue) {
        String cachedPropertyValue = getPropertyCache().get(systemPropertyKey);
        if (cachedPropertyValue != null) {
            return cachedPropertyValue;
        } else {
            if (isBlank(environmentVariableKey)) {
                throw new IllegalArgumentException("environment property name cannot be null for " + systemPropertyKey);
            }
            String defaultOrEnvironmentVariable = isNotBlank(System.getenv(environmentVariableKey)) ? System.getenv(environmentVariableKey) : defaultValue;
            String propertyValue = System.getProperty(systemPropertyKey, properties != null ? properties.getProperty(systemPropertyKey, defaultOrEnvironmentVariable) : defaultOrEnvironmentVariable);
            if (propertyValue != null && propertyValue.startsWith("\"") && propertyValue.endsWith("\"")) {
                propertyValue = propertyValue.replaceAll("^\"|\"$", "");
            }
            getPropertyCache().put(systemPropertyKey, propertyValue);
            return propertyValue;
        }
    }
}
