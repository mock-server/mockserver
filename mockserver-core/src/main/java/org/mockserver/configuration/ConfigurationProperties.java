package org.mockserver.configuration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.memory.MemoryMonitoring;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.slf4j.event.Level;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.logging.MockServerLogger.configureLogger;
import static org.mockserver.model.HttpRequest.request;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public class ConfigurationProperties {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(ConfigurationProperties.class);
    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final long DEFAULT_MAX_TIMEOUT = 20;
    private static final int DEFAULT_CONNECT_TIMEOUT = 20000;
    private static final String DEFAULT_MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS = "false";
    private static final int DEFAULT_MAX_FUTURE_TIMEOUT = 60;
    private static final String DEFAULT_OUTPUT_MEMORY_USAGE_CSV = "false";
    private static final int DEFAULT_MAX_WEB_SOCKET_EXPECTATIONS = 1500;
    private static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_HEADER_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_CHUNK_SIZE = Integer.MAX_VALUE;
    private static final String DEFAULT_ENABLE_CORS_FOR_API = "false";
    private static final String DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES = "false";
    private static final String DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE = "false";
    private static final int DEFAULT_NIO_EVENT_LOOP_THREAD_COUNT = 5;
    private static final int DEFAULT_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT = 5;
    private static final int DEFAULT_ACTION_HANDLER_THREAD_COUNT = Math.max(5, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT = 5;
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "org/mockserver/socket/PKCS8CertificateAuthorityPrivateKey.pem";
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "org/mockserver/socket/CertificateAuthorityCertificate.pem";
    private static final String DEFAULT_MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE = "false";
    private static final String DEFAULT_TLS_MUTUAL_AUTHENTICATION_REQUIRED = "false";
    private static final String DEFAULT_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN = "";
    private static final String DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE = "ANY";
    private static final String DEFAULT_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES = "";
    private static final String DEFAULT_FORWARD_PROXY_TLS_PRIVATE_KEY = "";
    private static final String DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN = "";
    private static final String DEFAULT_CORS_ALLOW_HEADERS = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization";
    private static final String DEFAULT_CORS_ALLOW_METHODS = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE";
    private static final String DEFAULT_CORS_ALLOW_CREDENTIALS = "true";
    private static final int DEFAULT_CORS_MAX_AGE_IN_SECONDS = 300;
    private static final String DEFAULT_LIVENESS_HTTP_GET_PATH = "";

    private static final String MOCKSERVER_PROPERTY_FILE = "mockserver.propertyFile";
    private static final String MOCKSERVER_ENABLE_CORS_FOR_API = "mockserver.enableCORSForAPI";
    private static final String MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES = "mockserver.enableCORSForAllResponses";
    private static final String MOCKSERVER_MAX_EXPECTATIONS = "mockserver.maxExpectations";
    private static final String MOCKSERVER_MAX_LOG_ENTRIES = "mockserver.maxLogEntries";
    private static final String MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV = "mockserver.outputMemoryUsageCsv";
    private static final String MOCKSERVER_MEMORY_USAGE_DIRECTORY = "mockserver.memoryUsageCsvDirectory";
    private static final String MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS = "mockserver.maxWebSocketExpectations";
    private static final String MOCKSERVER_MAX_INITIAL_LINE_LENGTH = "mockserver.maxInitialLineLength";
    private static final String MOCKSERVER_MAX_HEADER_SIZE = "mockserver.maxHeaderSize";
    private static final String MOCKSERVER_MAX_CHUNK_SIZE = "mockserver.maxChunkSize";
    private static final String MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.nioEventLoopThreadCount";
    private static final String MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.clientNioEventLoopThreadCount";
    private static final String MOCKSERVER_ACTION_HANDLER_THREAD_COUNT = "mockserver.actionHandlerThreadCount";
    private static final String MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT = "mockserver.webSocketClientEventLoopThreadCount";
    private static final String MOCKSERVER_MAX_SOCKET_TIMEOUT = "mockserver.maxSocketTimeout";
    private static final String MOCKSERVER_MAX_FUTURE_TIMEOUT = "mockserver.maxFutureTimeout";
    private static final String MOCKSERVER_SOCKET_CONNECTION_TIMEOUT = "mockserver.socketConnectionTimeout";
    private static final String MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS = "mockserver.alwaysCloseSocketConnections";
    private static final String MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME = "mockserver.sslCertificateDomainName";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS = "mockserver.sslSubjectAlternativeNameDomains";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS = "mockserver.sslSubjectAlternativeNameIps";
    private static final String MOCKSERVER_USE_BOUNCY_CASTLE_FOR_KEY_AND_CERTIFICATE_GENERATION = "mockserver.useBouncyCastleForKeyAndCertificateGeneration";
    private static final String MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE = "mockserver.preventCertificateDynamicUpdate";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "mockserver.certificateAuthorityPrivateKey";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "mockserver.certificateAuthorityCertificate";
    private static final String MOCKSERVER_TLS_PRIVATE_KEY_PATH = "mockserver.privateKeyPath";
    private static final String MOCKSERVER_TLS_X509_CERTIFICATE_PATH = "mockserver.x509CertificatePath";
    private static final String MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE = "mockserver.dynamicallyCreateCertificateAuthorityCertificate";
    private static final String MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE = "mockserver.directoryToSaveDynamicSSLCertificate";
    private static final String MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED = "mockserver.tlsMutualAuthenticationRequired";
    private static final String MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN = "mockserver.tlsMutualAuthenticationCertificateChain";
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE = "mockserver.forwardProxyTLSX509CertificatesTrustManagerType";
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES = "mockserver.forwardProxyTLSCustomTrustX509Certificates";
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY = "mockserver.forwardProxyPrivateKey";
    private static final String MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN = "mockserver.forwardProxyCertificateChain";
    private static final String MOCKSERVER_LOG_LEVEL = "mockserver.logLevel";
    private static final String MOCKSERVER_METRICS_ENABLED = "mockserver.metricsEnabled";
    private static final String MOCKSERVER_DISABLE_SYSTEM_OUT = "mockserver.disableSystemOut";
    private static final String MOCKSERVER_DETAILED_MATCH_FAILURES = "mockserver.detailedMatchFailures";
    private static final String MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG = "mockserver.launchUIForLogLevelDebug";
    private static final String MOCKSERVER_MATCHERS_FAIL_FAST = "mockserver.matchersFailFast";
    private static final String MOCKSERVER_LOCAL_BOUND_IP = "mockserver.localBoundIP";
    private static final String MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION = "mockserver.attemptToProxyIfNoMatchingExpectation";
    @Deprecated
    private static final String MOCKSERVER_HTTP_PROXY = "mockserver.httpProxy";
    @Deprecated
    private static final String MOCKSERVER_HTTPS_PROXY = "mockserver.httpsProxy";
    @Deprecated
    private static final String MOCKSERVER_SOCKS_PROXY = "mockserver.socksProxy";
    private static final String MOCKSERVER_FORWARD_HTTP_PROXY = "mockserver.forwardHttpProxy";
    private static final String MOCKSERVER_FORWARD_HTTPS_PROXY = "mockserver.forwardHttpsProxy";
    private static final String MOCKSERVER_FORWARD_SOCKS_PROXY = "mockserver.forwardSocksProxy";
    private static final String MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME = "mockserver.forwardProxyAuthenticationUsername";
    private static final String MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD = "mockserver.forwardProxyAuthenticationPassword";
    private static final String MOCKSERVER_PROXY_SERVER_REALM = "mockserver.proxyAuthenticationRealm";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_USERNAME = "mockserver.proxyAuthenticationUsername";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD = "mockserver.proxyAuthenticationPassword";
    private static final String MOCKSERVER_INITIALIZATION_CLASS = "mockserver.initializationClass";
    private static final String MOCKSERVER_INITIALIZATION_JSON_PATH = "mockserver.initializationJsonPath";
    private static final String MOCKSERVER_WATCH_INITIALIZATION_JSON = "mockserver.watchInitializationJson";
    private static final String MOCKSERVER_PERSISTED_EXPECTATIONS_PATH = "mockserver.persistedExpectationsPath";
    private static final String MOCKSERVER_PERSIST_EXPECTATIONS = "mockserver.persistExpectations";
    private static final String MOCKSERVER_CORS_ALLOW_HEADERS = "mockserver.corsAllowHeaders";
    private static final String MOCKSERVER_CORS_ALLOW_METHODS = "mockserver.corsAllowMethods";
    private static final String MOCKSERVER_CORS_ALLOW_CREDENTIALS = "mockserver.corsAllowCredentials";
    private static final String MOCKSERVER_CORS_MAX_AGE_IN_SECONDS = "mockserver.corsMaxAgeInSeconds";
    private static final String MOCKSERVER_LIVENESS_HTTP_GET_PATH = "mockserver.livenessHttpGetPath";

    private static final Properties PROPERTIES = readPropertyFile();
    private static final Set<String> ALL_SUBJECT_ALTERNATIVE_DOMAINS = Sets.newConcurrentHashSet();
    private static final Set<String> ALL_SUBJECT_ALTERNATIVE_IPS = Sets.newConcurrentHashSet();
    private static final AtomicBoolean REBUILD_KEY_STORE = new AtomicBoolean(false);
    private static final AtomicBoolean REBUILD_SERVER_KEY_STORE = new AtomicBoolean(false);
    private static final IntegerStringListParser INTEGER_STRING_LIST_PARSER = new IntegerStringListParser();

    static {
        addSslSubjectAlternativeNameDomains(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS", "localhost").split(","));
        addSslSubjectAlternativeNameIps(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS", "127.0.0.1,0.0.0.0").split(","));
    }

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

    private static final List<String> forwardProxyTLSX509CertificatesTrustManagerValues = Arrays.stream(ForwardProxyTLSX509CertificatesTrustManager.values()).map(Enum::name).collect(Collectors.toList());

    private static ForwardProxyTLSX509CertificatesTrustManager validateTrustManagerType(String trustManagerType) {
        if (!forwardProxyTLSX509CertificatesTrustManagerValues.contains(trustManagerType)) {
            new IllegalArgumentException("Invalid value for ForwardProxyTLSX509CertificatesTrustManager \"" + trustManagerType + "\" the only supported values are: " + forwardProxyTLSX509CertificatesTrustManagerValues).printStackTrace();
            return null;
        } else {
            return ForwardProxyTLSX509CertificatesTrustManager.valueOf(trustManagerType);
        }
    }

    private static MemoryMonitoring memoryMonitoring = new MemoryMonitoring();
    private static int defaultMaxExpectations = memoryMonitoring.startingMaxExpectations();
    private static int defaultMaxLogEntries = memoryMonitoring.startingMaxLogEntries();
    private static Level logLevel = Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()));
    private static String javaLoggerLogLevel = getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase());
    private static boolean metricsEnabled = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
    private static boolean disableSystemOut = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
    private static boolean detailedMatchFailures = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
    private static boolean matchersFailFast = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DETAILED_MATCH_FAILURES, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
    private static boolean attemptToProxyIfNoMatchingExpectation = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION", "" + true));
    private static boolean enableMTLS = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED", DEFAULT_TLS_MUTUAL_AUTHENTICATION_REQUIRED));
    private static String tlsMutualAuthenticationCertificateChain = readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", DEFAULT_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN);
    private static ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType = validateTrustManagerType(readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE));
    private static String forwardProxyTLSCustomTrustX509Certificates = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, "MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES", DEFAULT_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES);
    private static String forwardProxyPrivateKey = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, "MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY", DEFAULT_FORWARD_PROXY_TLS_PRIVATE_KEY);
    private static String forwardProxyCertificateChain = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN);
    private static boolean enableCORSForAPI = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", DEFAULT_ENABLE_CORS_FOR_API));
    private static boolean enableCORSForAPIHasBeenSetExplicitly = System.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null || PROPERTIES.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null;
    private static boolean enableCORSForAllResponses = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES));
    private static int maxInitialLineLength = readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", DEFAULT_MAX_INITIAL_LINE_LENGTH);
    private static int maxHeaderSize = readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", DEFAULT_MAX_HEADER_SIZE);
    private static int maxChunkSize = readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", DEFAULT_MAX_CHUNK_SIZE);
    private static boolean preventCertificateDynamicUpdate = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE));
    private static boolean alwaysCloseConnections = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS", DEFAULT_MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS));
    private static String livenessHttpGetPath = readPropertyHierarchically(MOCKSERVER_LIVENESS_HTTP_GET_PATH, "MOCKSERVER_LIVENESS_HTTP_GET_PATH", DEFAULT_LIVENESS_HTTP_GET_PATH);

    @VisibleForTesting
    static void reset() {
        ALL_SUBJECT_ALTERNATIVE_DOMAINS.clear();
        ALL_SUBJECT_ALTERNATIVE_IPS.clear();
        REBUILD_KEY_STORE.set(false);
        REBUILD_SERVER_KEY_STORE.set(false);
        logLevel = Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()));
        javaLoggerLogLevel = getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase());
        metricsEnabled = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
        disableSystemOut = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
        detailedMatchFailures = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DETAILED_MATCH_FAILURES, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
        matchersFailFast = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DETAILED_MATCH_FAILURES, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
        attemptToProxyIfNoMatchingExpectation = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION", "" + true));
        enableMTLS = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED", DEFAULT_TLS_MUTUAL_AUTHENTICATION_REQUIRED));
        tlsMutualAuthenticationCertificateChain = readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", DEFAULT_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN);
        forwardProxyTLSX509CertificatesTrustManagerType = validateTrustManagerType(readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE));
        forwardProxyTLSCustomTrustX509Certificates = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, "MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES", DEFAULT_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES);
        forwardProxyPrivateKey = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, "MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY", DEFAULT_FORWARD_PROXY_TLS_PRIVATE_KEY);
        forwardProxyCertificateChain = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN);
        enableCORSForAPI = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", DEFAULT_ENABLE_CORS_FOR_API));
        enableCORSForAPIHasBeenSetExplicitly = System.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null || PROPERTIES.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null;
        enableCORSForAllResponses = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES));
        maxInitialLineLength = readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", DEFAULT_MAX_INITIAL_LINE_LENGTH);
        maxHeaderSize = readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", DEFAULT_MAX_HEADER_SIZE);
        maxChunkSize = readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", DEFAULT_MAX_CHUNK_SIZE);
        preventCertificateDynamicUpdate = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE));
        alwaysCloseConnections = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS", DEFAULT_MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS));
        livenessHttpGetPath = readPropertyHierarchically(MOCKSERVER_LIVENESS_HTTP_GET_PATH, "MOCKSERVER_LIVENESS_HTTP_GET_PATH", DEFAULT_LIVENESS_HTTP_GET_PATH);
    }

    private static String propertyFile() {
        return System.getProperty(MOCKSERVER_PROPERTY_FILE, isBlank(System.getenv("MOCKSERVER_PROPERTY_FILE")) ? "mockserver.properties" : System.getenv("MOCKSERVER_PROPERTY_FILE"));
    }

    public static int defaultMaxExpectations() {
        return defaultMaxExpectations;
    }

    public static void defaultMaxExpectations(int defaultMaxExpectations) {
        ConfigurationProperties.defaultMaxExpectations = defaultMaxExpectations;
    }

    public static int maxExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_EXPECTATIONS, "MOCKSERVER_MAX_EXPECTATIONS", defaultMaxExpectations());
    }

    public static void maxExpectations(int count) {
        System.setProperty(MOCKSERVER_MAX_EXPECTATIONS, "" + count);
    }

    public static int defaultMaxLogEntries() {
        return defaultMaxLogEntries;
    }

    public static void defaultMaxLogEntries(int defaultMaxLogEntries) {
        ConfigurationProperties.defaultMaxLogEntries = defaultMaxLogEntries;
    }

    public static int maxLogEntries() {
        return readIntegerProperty(MOCKSERVER_MAX_LOG_ENTRIES, "MOCKSERVER_MAX_LOG_ENTRIES", defaultMaxLogEntries());
    }

    public static void maxLogEntries(int count) {
        System.setProperty(MOCKSERVER_MAX_LOG_ENTRIES, "" + count);
    }

    public static int ringBufferSize() {
        return nextPowerOfTwo(Math.min(defaultMaxLogEntries(), 1500));
    }

    private static int nextPowerOfTwo(int value) {
        for (int i = 0; i < 16; i++) {
            double powOfTwo = Math.pow(2, i);
            if (powOfTwo > value) {
                return (int) powOfTwo;
            }
        }
        return (int) Math.pow(2, 16);
    }

    public static boolean outputMemoryUsageCsv() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV, "MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV", DEFAULT_OUTPUT_MEMORY_USAGE_CSV));
    }

    public static void outputMemoryUsageCsv(boolean enable) {
        System.setProperty(MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV, "" + enable);
    }

    public static String memoryUsageCsvDirectory() {
        return readPropertyHierarchically(MOCKSERVER_MEMORY_USAGE_DIRECTORY, "MOCKSERVER_MEMORY_USAGE_DIRECTORY", ".");
    }

    public static void memoryUsageCsvDirectory(String directory) {
        fileExists(directory);
        System.setProperty(MOCKSERVER_MEMORY_USAGE_DIRECTORY, directory);
    }

    public static int maxWebSocketExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, "MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS", DEFAULT_MAX_WEB_SOCKET_EXPECTATIONS);
    }

    public static void maxWebSocketExpectations(int count) {
        System.setProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, "" + count);
    }

    public static int maxInitialLineLength() {
        return maxInitialLineLength;
    }

    public static void maxInitialLineLength(int length) {
        System.setProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "" + length);
        maxInitialLineLength = readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", DEFAULT_MAX_INITIAL_LINE_LENGTH);
    }

    public static int maxHeaderSize() {
        return maxHeaderSize;
    }

    public static void maxHeaderSize(int size) {
        System.setProperty(MOCKSERVER_MAX_HEADER_SIZE, "" + size);
        maxHeaderSize = readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", DEFAULT_MAX_HEADER_SIZE);
    }

    public static int maxChunkSize() {
        return maxChunkSize;
    }

    public static void maxChunkSize(int size) {
        System.setProperty(MOCKSERVER_MAX_CHUNK_SIZE, "" + size);
        maxChunkSize = readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", DEFAULT_MAX_CHUNK_SIZE);
    }

    public static int nioEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT", DEFAULT_NIO_EVENT_LOOP_THREAD_COUNT);
    }

    public static void nioEventLoopThreadCount(int count) {
        System.setProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static int clientNioEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT", DEFAULT_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT);
    }

    public static void clientNioEventLoopThreadCount(int count) {
        System.setProperty(MOCKSERVER_CLIENT_NIO_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static int actionHandlerThreadCount() {
        return readIntegerProperty(MOCKSERVER_ACTION_HANDLER_THREAD_COUNT, "MOCKSERVER_ACTION_HANDLER_THREAD_COUNT", DEFAULT_ACTION_HANDLER_THREAD_COUNT);
    }

    public static void actionHandlerThreadCount(int count) {
        System.setProperty(MOCKSERVER_ACTION_HANDLER_THREAD_COUNT, "" + count);
    }

    public static int webSocketClientEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT, "MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT", DEFAULT_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT);
    }

    public static void webSocketClientEventLoopThreadCount(int count) {
        System.setProperty(MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static long maxSocketTimeout() {
        return readLongProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, "MOCKSERVER_MAX_SOCKET_TIMEOUT", TimeUnit.SECONDS.toMillis(DEFAULT_MAX_TIMEOUT));
    }

    public static void maxSocketTimeout(long milliseconds) {
        System.setProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, "" + milliseconds);
    }

    public static long maxFutureTimeout() {
        return readLongProperty(MOCKSERVER_MAX_FUTURE_TIMEOUT, "DEFAULT_MAX_FUTURE_TIMEOUT", TimeUnit.SECONDS.toMillis(DEFAULT_MAX_FUTURE_TIMEOUT));
    }

    public static void maxFutureTimeout(long milliseconds) {
        System.setProperty(MOCKSERVER_MAX_FUTURE_TIMEOUT, "" + milliseconds);
    }

    public static int socketConnectionTimeout() {
        return readIntegerProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, "MOCKSERVER_SOCKET_CONNECTION_TIMEOUT", DEFAULT_CONNECT_TIMEOUT);
    }

    public static void socketConnectionTimeout(int milliseconds) {
        System.setProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, "" + milliseconds);
    }

    public static void alwaysCloseSocketConnections(boolean alwaysClose) {
        System.setProperty(MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "" + alwaysClose);
        alwaysCloseConnections = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS, "MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS", DEFAULT_MOCKSERVER_ALWAYS_CLOSE_SOCKET_CONNECTIONS));
    }

    public static boolean alwaysCloseSocketConnections() {
        return alwaysCloseConnections;
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, "MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME", CertificateSigningRequest.CERTIFICATE_DOMAIN);
    }

    public static void sslCertificateDomainName(String domainName) {
        System.setProperty(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, domainName);
        rebuildServerTLSContext(true);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void addSubjectAlternativeName(String host) {
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

    public static String[] sslSubjectAlternativeNameDomains() {
        return ALL_SUBJECT_ALTERNATIVE_DOMAINS.toArray(new String[0]);
    }

    public static void addSslSubjectAlternativeNameDomains(String... additionalSubjectAlternativeNameDomains) {
        boolean subjectAlternativeDomainsModified = false;
        for (String subjectAlternativeDomain : additionalSubjectAlternativeNameDomains) {
            if (ALL_SUBJECT_ALTERNATIVE_DOMAINS.add(subjectAlternativeDomain.trim())) {
                subjectAlternativeDomainsModified = true;
            }
        }
        if (subjectAlternativeDomainsModified) {
            System.setProperty(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS, Joiner.on(",").join(new TreeSet<>(ALL_SUBJECT_ALTERNATIVE_DOMAINS)));
            rebuildServerTLSContext(true);
        }
    }

    public static void clearSslSubjectAlternativeNameDomains() {
        ALL_SUBJECT_ALTERNATIVE_DOMAINS.clear();
    }

    @SuppressWarnings("unused")
    public static boolean containsSslSubjectAlternativeName(String domainOrIp) {
        return ALL_SUBJECT_ALTERNATIVE_DOMAINS.contains(domainOrIp) || ALL_SUBJECT_ALTERNATIVE_IPS.contains(domainOrIp);
    }

    public static String[] sslSubjectAlternativeNameIps() {
        return ALL_SUBJECT_ALTERNATIVE_IPS.toArray(new String[0]);
    }

    public static void addSslSubjectAlternativeNameIps(String... additionalSubjectAlternativeNameIps) {
        boolean subjectAlternativeIpsModified = false;
        for (String subjectAlternativeIp : additionalSubjectAlternativeNameIps) {
            if (ALL_SUBJECT_ALTERNATIVE_IPS.add(subjectAlternativeIp.trim())) {
                subjectAlternativeIpsModified = true;
            }
        }
        if (subjectAlternativeIpsModified) {
            System.setProperty(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, Joiner.on(",").join(new TreeSet<>(ALL_SUBJECT_ALTERNATIVE_IPS)));
            rebuildServerTLSContext(true);
        }
    }

    public static void clearSslSubjectAlternativeNameIps() {
        ALL_SUBJECT_ALTERNATIVE_IPS.clear();
        addSslSubjectAlternativeNameIps(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS", "127.0.0.1,0.0.0.0").split(","));
    }

    public static boolean rebuildTLSContext() {
        return REBUILD_KEY_STORE.get();
    }

    public static void rebuildTLSContext(boolean rebuildTLSContext) {
        ConfigurationProperties.REBUILD_KEY_STORE.set(rebuildTLSContext);
    }

    public static boolean rebuildServerTLSContext() {
        return REBUILD_SERVER_KEY_STORE.get();
    }

    public static void rebuildServerTLSContext(boolean rebuildServerTLSContext) {
        ConfigurationProperties.REBUILD_SERVER_KEY_STORE.set(rebuildServerTLSContext);
    }

    /**
     * Use BouncyCastle instead of the Java JDK to generate Certificate and Keys, this is helpful if:
     * - using the IBM JVM or
     * - avoiding bugs in the X509 creation logic Java JDK (i.e. such as the format of SAN and CN)
     * <p>
     * When enabling this setting the following dependencies must be provided on the classpath (they are not included with MockServer)
     * <pre>&lt;dependency&gt;
     *   &lt;groupId&gt;org.bouncycastle&lt;/groupId&gt;
     *   &lt;artifactId&gt;bcprov-jdk15on&lt;/artifactId&gt;
     *   &lt;version&gt;1.70&lt;/version&gt;
     * &lt;/dependency&gt;
     * &lt;dependency&gt;
     *   &lt;groupId&gt;org.bouncycastle&lt;/groupId&gt;
     *   &lt;artifactId&gt;bcpkix-jdk15on&lt;/artifactId&gt;
     *   &lt;version&gt;1.70&lt;/version&gt;
     * &lt;/dependency&gt;</pre>
     *
     * @param enable enable BouncyCastle instead of the Java JDK to generate Certificate and Keys
     */
    public static void useBouncyCastleForKeyAndCertificateGeneration(boolean enable) {
        System.setProperty(MOCKSERVER_USE_BOUNCY_CASTLE_FOR_KEY_AND_CERTIFICATE_GENERATION, "" + enable);
    }

    public static boolean useBouncyCastleForKeyAndCertificateGeneration() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_USE_BOUNCY_CASTLE_FOR_KEY_AND_CERTIFICATE_GENERATION, "MOCKSERVER_USE_BOUNCY_CASTLE_FOR_KEY_AND_CERTIFICATE_GENERATION", "false"));
    }

    /**
     * Prevent certificates from dynamically updating when domain list changes
     *
     * @param prevent prevent certificates from dynamically updating when domain list changes
     */
    public static void preventCertificateDynamicUpdate(boolean prevent) {
        System.setProperty(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "" + prevent);
        preventCertificateDynamicUpdate = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE));
    }

    public static boolean preventCertificateDynamicUpdate() {
        return preventCertificateDynamicUpdate;
    }

    public static String certificateAuthorityPrivateKey() {
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY, "MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY", DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
    }

    /**
     * File location of custom Private Key for Certificate Authority for TLS, the private key must be a PKCS#8 PEM file and must match the certificateAuthorityCertificate
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     *
     * @param certificateAuthorityPrivateKey location of the PEM file containing the certificate authority private key
     */
    public static void certificateAuthorityPrivateKey(String certificateAuthorityPrivateKey) {
        System.setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY, certificateAuthorityPrivateKey);
    }

    public static String certificateAuthorityCertificate() {
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, "MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE", DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
    }

    /**
     * File location of custom X.509 Certificate for Certificate Authority for TLS, the certificate must be a X509 PEM file and must match the certificateAuthorityPrivateKey
     *
     * @param certificateAuthorityCertificate location of the PEM file containing the certificate authority X509 certificate
     */
    public static void certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        fileExists(certificateAuthorityCertificate);
        System.setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, certificateAuthorityCertificate);
    }

    public static boolean dynamicallyCreateCertificateAuthorityCertificate() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE, "MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE", DEFAULT_MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE));
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
        System.setProperty(MOCKSERVER_DYNAMICALLY_CREATE_CERTIFICATE_AUTHORITY_CERTIFICATE, "" + enable);
    }

    public static String directoryToSaveDynamicSSLCertificate() {
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, "MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE", "");
    }

    /**
     * Directory used to save the dynamically generated Certificate Authority X.509 Certificate and Private Key.
     *
     * @param directoryToSaveDynamicSSLCertificate directory to save Certificate Authority X.509 Certificate and Private Key
     */
    public static void directoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        fileExists(directoryToSaveDynamicSSLCertificate);
        System.setProperty(MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, directoryToSaveDynamicSSLCertificate);
    }

    public static String privateKeyPath() {
        return readPropertyHierarchically(MOCKSERVER_TLS_PRIVATE_KEY_PATH, "MOCKSERVER_TLS_PRIVATE_KEY_PATH", "");
    }

    /**
     * File location of a fixed custom private key for TLS connections into MockServer.
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
    public static void privateKeyPath(String privateKeyPath) {
        fileExists(privateKeyPath);
        System.setProperty(MOCKSERVER_TLS_PRIVATE_KEY_PATH, privateKeyPath);
    }


    public static String x509CertificatePath() {
        return readPropertyHierarchically(MOCKSERVER_TLS_X509_CERTIFICATE_PATH, "MOCKSERVER_TLS_X509_CERTIFICATE_PATH", "");
    }

    /**
     * File location of a fixed custom X.509 Certificate for TLS connections into MockServer.
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
        System.setProperty(MOCKSERVER_TLS_X509_CERTIFICATE_PATH, x509CertificatePath);
    }

    public static boolean tlsMutualAuthenticationRequired() {
        return enableMTLS;
    }

    /**
     * Require mTLS (also called client authentication and two-way TLS) for all TLS connections / HTTPS requests to MockServer
     *
     * @param enable TLS mutual authentication
     */
    public static void tlsMutualAuthenticationRequired(boolean enable) {
        System.setProperty(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "" + enable);
        enableMTLS = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED", DEFAULT_TLS_MUTUAL_AUTHENTICATION_REQUIRED));
    }

    public static String tlsMutualAuthenticationCertificateChain() {
        return tlsMutualAuthenticationCertificateChain;
    }

    /**
     * File location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer performs mTLS (client authentication) for inbound TLS connections because tlsMutualAuthenticationRequired is enabled
     *
     * @param trustCertificateChain file location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates
     */
    public static void tlsMutualAuthenticationCertificateChain(String trustCertificateChain) {
        System.setProperty(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "" + trustCertificateChain);
        tlsMutualAuthenticationCertificateChain = readPropertyHierarchically(MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN, "MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN", DEFAULT_TLS_MUTUAL_AUTHENTICATION_CERTIFICATE_CHAIN);
    }

    public static ForwardProxyTLSX509CertificatesTrustManager forwardProxyTLSX509CertificatesTrustManagerType() {
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
     * @param trustManagerType trusted set of certificates for forwarded or proxied requests, allowed values: ALL, JVM, CUSTOM.
     */
    public static void forwardProxyTLSX509CertificatesTrustManagerType(String trustManagerType) {
        System.setProperty(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, trustManagerType);
        forwardProxyTLSX509CertificatesTrustManagerType = validateTrustManagerType(readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATES_TRUST_MANAGER_TYPE));
    }

    public static String forwardProxyTLSCustomTrustX509Certificates() {
        return forwardProxyTLSCustomTrustX509Certificates;
    }

    /**
     * File location of custom file for trusted X509 Certificate Authority roots for forwarded or proxied requests, the certificate chain must be a X509 PEM file.
     * <p>
     * MockServer will only be able to establish a TLS connection to endpoints that have an X509 certificate chain that is signed by one of the provided custom
     * certificates, i.e. where a path can be established from the endpoints X509 certificate to one or more of the custom X509 certificates provided.
     *
     * @param customX509Certificates custom set of trusted X509 certificate authority roots for forwarded or proxied requests in PEM format.
     */
    public static void forwardProxyTLSCustomTrustX509Certificates(String customX509Certificates) {
        fileExists(customX509Certificates);
        System.setProperty(MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, customX509Certificates);
        forwardProxyTLSCustomTrustX509Certificates = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES, "MOCKSERVER_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES", DEFAULT_FORWARD_PROXY_TLS_CUSTOM_TRUST_X509_CERTIFICATES);
    }

    public static String forwardProxyPrivateKey() {
        return forwardProxyPrivateKey;
    }

    /**
     * File location of custom Private Key for proxied TLS connections out of MockServer, the private key must be a PKCS#8 PEM file
     * <p>
     * To convert a PKCS#1 (i.e. default for Bouncy Castle) to a PKCS#8 the following command can be used: openssl pkcs8 -topk8 -inform PEM -in private_key_PKCS_1.pem -out private_key_PKCS_8.pem -nocrypt
     * <p>
     * This private key will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param privateKey location of the PEM file containing the private key
     */
    public static void forwardProxyPrivateKey(String privateKey) {
        fileExists(privateKey);
        System.setProperty(MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, privateKey);
        forwardProxyPrivateKey = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY, "MOCKSERVER_FORWARD_PROXY_TLS_PRIVATE_KEY", DEFAULT_FORWARD_PROXY_TLS_PRIVATE_KEY);
    }

    public static String forwardProxyCertificateChain() {
        return forwardProxyCertificateChain;
    }

    /**
     * File location of custom mTLS (TLS client authentication) X.509 Certificate Chain for Trusting (i.e. signature verification of) Client X.509 Certificates, the certificate chain must be a X509 PEM file.
     * <p>
     * This certificate chain will be used if MockServer needs to perform mTLS (client authentication) for outbound TLS connections.
     *
     * @param certificateChain location of the PEM file containing the certificate chain
     */
    public static void forwardProxyCertificateChain(String certificateChain) {
        fileExists(certificateChain);
        System.setProperty(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, certificateChain);
        forwardProxyCertificateChain = readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN, "MOCKSERVER_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN", DEFAULT_FORWARD_PROXY_TLS_X509_CERTIFICATE_CHAIN);
    }

    public static Level logLevel() {
        return logLevel;
    }

    public static String javaLoggerLogLevel() {
        return javaLoggerLogLevel;
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
            System.setProperty(MOCKSERVER_LOG_LEVEL, level);
            if (getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()).equals("OFF")) {
                logLevel = null;
                javaLoggerLogLevel = "OFF";
            } else {
                logLevel = Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()));
                javaLoggerLogLevel = getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase());
            }
        }
        configureLogger();
    }

    public static boolean disableSystemOut() {
        return disableSystemOut;
    }

    public static void disableSystemOut(boolean disable) {
        System.setProperty(MOCKSERVER_DISABLE_SYSTEM_OUT, "" + disable);
        disableSystemOut = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
        configureLogger();
    }

    public static boolean detailedMatchFailures() {
        return detailedMatchFailures;
    }

    /**
     * If true (the default) the log event recording that a request matcher did not match will include a detailed reason why each non matching field did not match.
     *
     * @param enable enabled detailed match failure log events
     */
    public static void detailedMatchFailures(boolean enable) {
        System.setProperty(MOCKSERVER_DETAILED_MATCH_FAILURES, "" + enable);
        detailedMatchFailures = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DETAILED_MATCH_FAILURES, "MOCKSERVER_DETAILED_MATCH_FAILURES", "" + true));
    }

    public static boolean launchUIForLogLevelDebug() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG, "MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG", "" + false));
    }

    /**
     * If true (the default) the ClientAndServer constructor will open the UI in the default browser when the log level is set to DEBUG.
     *
     * @param enable enabled ClientAndServer constructor launching UI when log level is DEBUG
     */
    public static void launchUIForLogLevelDebug(boolean enable) {
        System.setProperty(MOCKSERVER_LAUNCH_UI_FOR_LOG_LEVEL_DEBUG, "" + enable);
    }

    public static boolean matchersFailFast() {
        return matchersFailFast;
    }

    /**
     * If true (the default) request matchers will fail on the first non-matching field, if false request matchers will compare all fields.
     * This is useful to see all mismatching fields in the log event recording that a request matcher did not match.
     *
     * @param enable enabled request matchers failing fast
     */
    public static void matchersFailFast(boolean enable) {
        System.setProperty(MOCKSERVER_MATCHERS_FAIL_FAST, "" + enable);
        matchersFailFast = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_MATCHERS_FAIL_FAST, "MOCKSERVER_MATCHERS_FAIL_FAST", "" + true));
    }

    public static boolean metricsEnabled() {
        return metricsEnabled;
    }

    public static void metricsEnabled(boolean enable) {
        System.setProperty(MOCKSERVER_METRICS_ENABLED, "" + enable);
        metricsEnabled = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
    }

    public static String localBoundIP() {
        return readPropertyHierarchically(MOCKSERVER_LOCAL_BOUND_IP, "MOCKSERVER_LOCAL_BOUND_IP", "");
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void localBoundIP(String localBoundIP) {
        System.setProperty(MOCKSERVER_LOCAL_BOUND_IP, InetAddresses.forString(localBoundIP).getHostAddress());
    }

    public static boolean attemptToProxyIfNoMatchingExpectation() {
        return attemptToProxyIfNoMatchingExpectation;
    }

    /**
     * If true (the default) when no matching expectation is found, and the host header of the request does not match MockServer's host, then MockServer attempts to proxy the request if that fails then a 404 is returned.
     * If false when no matching expectation is found, and MockServer is not being used as a proxy, then MockServer always returns a 404 immediately.
     *
     * @param enable enables automatically attempted proxying of request that don't match an expectation and look like they should be proxied
     */
    public static void attemptToProxyIfNoMatchingExpectation(boolean enable) {
        System.setProperty(MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "" + enable);
        attemptToProxyIfNoMatchingExpectation = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION, "MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION", "" + true));
    }

    /**
     * @deprecated use forwardHttpProxy instead
     */
    @Deprecated
    public static InetSocketAddress httpProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_HTTP_PROXY, "MOCKSERVER_HTTP_PROXY");
    }

    /**
     * @deprecated use forwardHttpProxy instead
     */
    @Deprecated
    public static void httpProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "httpProxy", MOCKSERVER_HTTP_PROXY);
    }

    /**
     * @deprecated use forwardHttpsProxy instead
     */
    @Deprecated
    public static InetSocketAddress httpsProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_HTTPS_PROXY, "MOCKSERVER_HTTPS_PROXY");
    }

    /**
     * @deprecated use forwardHttpsProxy instead
     */
    @Deprecated
    public static void httpsProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "httpsProxy", MOCKSERVER_HTTPS_PROXY);
    }

    /**
     * @deprecated use forwardSocksProxy instead
     */
    @Deprecated
    public static InetSocketAddress socksProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_SOCKS_PROXY, "MOCKSERVER_SOCKS_PROXY");
    }

    /**
     * @deprecated use forwardSocksProxy instead
     */
    @Deprecated
    public static void socksProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "socksProxy", MOCKSERVER_SOCKS_PROXY);
    }

    public static InetSocketAddress forwardHttpProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_HTTP_PROXY, "MOCKSERVER_FORWARD_HTTP_PROXY");
    }

    public static void forwardHttpProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "forwardHttpProxy", MOCKSERVER_FORWARD_HTTP_PROXY);
    }

    public static InetSocketAddress forwardHttpsProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_HTTPS_PROXY, "MOCKSERVER_FORWARD_HTTPS_PROXY");
    }

    public static void forwardHttpsProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "forwardHttpsProxy", MOCKSERVER_FORWARD_HTTPS_PROXY);
    }

    public static InetSocketAddress forwardSocksProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_FORWARD_SOCKS_PROXY, "MOCKSERVER_FORWARD_SOCKS_PROXY");
    }

    public static void forwardSocksProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "forwardSocksProxy", MOCKSERVER_FORWARD_SOCKS_PROXY);
    }

    public static String forwardProxyAuthenticationUsername() {
        return readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME, "MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME", null);
    }

    public static void forwardProxyAuthenticationUsername(String forwardProxyAuthenticationUsername) {
        if (forwardProxyAuthenticationUsername != null) {
            System.setProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME, forwardProxyAuthenticationUsername);
        } else {
            System.clearProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_USERNAME);
        }
    }

    public static String forwardProxyAuthenticationPassword() {
        return readPropertyHierarchically(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD, "MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD", null);
    }

    public static void forwardProxyAuthenticationPassword(String forwardProxyAuthenticationPassword) {
        if (forwardProxyAuthenticationPassword != null) {
            System.setProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD, forwardProxyAuthenticationPassword);
        } else {
            System.clearProperty(MOCKSERVER_FORWARD_PROXY_AUTHENTICATION_PASSWORD);
        }
    }

    public static String proxyAuthenticationRealm() {
        return readPropertyHierarchically(MOCKSERVER_PROXY_SERVER_REALM, "MOCKSERVER_PROXY_SERVER_REALM", "MockServer HTTP Proxy");
    }

    public static void proxyAuthenticationRealm(String proxyAuthenticationRealm) {
        System.setProperty(MOCKSERVER_PROXY_SERVER_REALM, proxyAuthenticationRealm);
    }

    public static String proxyAuthenticationUsername() {
        return readPropertyHierarchically(MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, "MOCKSERVER_PROXY_AUTHENTICATION_USERNAME", "");
    }

    public static void proxyAuthenticationUsername(String proxyAuthenticationUsername) {
        System.setProperty(MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, proxyAuthenticationUsername);
    }

    public static String proxyAuthenticationPassword() {
        return readPropertyHierarchically(MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, "MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD", "");
    }

    public static void proxyAuthenticationPassword(String proxyAuthenticationPassword) {
        System.setProperty(MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, proxyAuthenticationPassword);
    }

    public static String initializationClass() {
        return readPropertyHierarchically(MOCKSERVER_INITIALIZATION_CLASS, "MOCKSERVER_INITIALIZATION_CLASS", "");
    }

    public static void initializationClass(String initializationClass) {
        System.setProperty(MOCKSERVER_INITIALIZATION_CLASS, initializationClass);
    }

    public static String initializationJsonPath() {
        return readPropertyHierarchically(MOCKSERVER_INITIALIZATION_JSON_PATH, "MOCKSERVER_INITIALIZATION_JSON_PATH", "");
    }

    public static void initializationJsonPath(String initializationJsonPath) {
        System.setProperty(MOCKSERVER_INITIALIZATION_JSON_PATH, initializationJsonPath);
    }

    public static boolean watchInitializationJson() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_WATCH_INITIALIZATION_JSON, "MOCKSERVER_WATCH_INITIALIZATION_JSON", "" + false));
    }

    public static void watchInitializationJson(boolean enable) {
        System.setProperty(MOCKSERVER_WATCH_INITIALIZATION_JSON, "" + enable);
    }

    public static boolean persistExpectations() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PERSIST_EXPECTATIONS, "MOCKSERVER_PERSIST_EXPECTATIONS", "" + false));
    }

    public static void persistExpectations(boolean enable) {
        System.setProperty(MOCKSERVER_PERSIST_EXPECTATIONS, "" + enable);
    }

    public static String persistedExpectationsPath() {
        return readPropertyHierarchically(MOCKSERVER_PERSISTED_EXPECTATIONS_PATH, "MOCKSERVER_PERSISTED_EXPECTATIONS_PATH", "persistedExpectations.json");
    }

    public static void persistedExpectationsPath(String persistedExpectationsPath) {
        System.setProperty(MOCKSERVER_PERSISTED_EXPECTATIONS_PATH, persistedExpectationsPath);
    }

    public static boolean enableCORSForAPI() {
        return enableCORSForAPI;
    }

    public static boolean enableCORSForAPIHasBeenSetExplicitly() {
        return enableCORSForAPIHasBeenSetExplicitly;
    }

    public static void enableCORSForAPI(boolean enable) {
        System.setProperty(MOCKSERVER_ENABLE_CORS_FOR_API, "" + enable);
        enableCORSForAPI = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", DEFAULT_ENABLE_CORS_FOR_API));
        enableCORSForAPIHasBeenSetExplicitly = true;
    }

    public static boolean enableCORSForAllResponses() {
        return enableCORSForAllResponses;
    }

    public static void enableCORSForAllResponses(boolean enable) {
        System.setProperty(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "" + enable);
        enableCORSForAllResponses = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES));
    }

    public static String corsAllowHeaders() {
        return readPropertyHierarchically(MOCKSERVER_CORS_ALLOW_HEADERS, "MOCKSERVER_CORS_ALLOW_HEADERS", DEFAULT_CORS_ALLOW_HEADERS);
    }

    public static void corsAllowHeaders(String corsAllowHeaders) {
        System.setProperty(MOCKSERVER_CORS_ALLOW_HEADERS, corsAllowHeaders);
    }

    public static String corsAllowMethods() {
        return readPropertyHierarchically(MOCKSERVER_CORS_ALLOW_METHODS, "MOCKSERVER_CORS_ALLOW_METHODS", DEFAULT_CORS_ALLOW_METHODS);
    }

    public static void corsAllowMethods(String corsAllowMethods) {
        System.setProperty(MOCKSERVER_CORS_ALLOW_METHODS, corsAllowMethods);
    }

    public static boolean corsAllowCredentials() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_CORS_ALLOW_CREDENTIALS, "MOCKSERVER_CORS_ALLOW_CREDENTIALS", DEFAULT_CORS_ALLOW_CREDENTIALS));
    }

    public static void corsAllowCredentials(boolean allow) {
        System.setProperty(MOCKSERVER_CORS_ALLOW_CREDENTIALS, "" + allow);
    }

    public static int corsMaxAgeInSeconds() {
        return readIntegerProperty(MOCKSERVER_CORS_MAX_AGE_IN_SECONDS, "MOCKSERVER_CORS_MAX_AGE_IN_SECONDS", DEFAULT_CORS_MAX_AGE_IN_SECONDS);
    }

    public static void corsMaxAgeInSeconds(int ageInSeconds) {
        System.setProperty(MOCKSERVER_CORS_MAX_AGE_IN_SECONDS, "" + ageInSeconds);
    }

    public static String livenessHttpGetPath() {
        return livenessHttpGetPath;
    }

    /**
     * Path to support HTTP GET requests for status response (also available on PUT /mockserver/status).
     * <p>
     * If this value is not modified then only PUT /mockserver/status but is a none blank value is provided for this value then GET requests to this path will return the 200 Ok status response showing the MockServer version and bound ports.
     * <p>
     * A GET request to this path will be matched before any expectation matching or proxying of requests.
     *
     * @param livenessPath path to support HTTP GET requests for status response
     */
    public static void livenessHttpGetPath(String livenessPath) {
        System.setProperty(MOCKSERVER_LIVENESS_HTTP_GET_PATH, livenessPath);
        livenessHttpGetPath = readPropertyHierarchically(MOCKSERVER_LIVENESS_HTTP_GET_PATH, "MOCKSERVER_LIVENESS_HTTP_GET_PATH", DEFAULT_LIVENESS_HTTP_GET_PATH);
    }

    @SuppressWarnings("ConstantConditions")
    private static void fileExists(String file) {
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


    private static void validateHostAndPort(String hostAndPort, String propertyName, String mockserverSocksProxy) {
        String errorMessage = "Invalid " + propertyName + " property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\"";
        try {
            URI uri = new URI("http://" + hostAndPort);
            if (uri.getHost() == null || uri.getPort() == -1) {
                throw new IllegalArgumentException(errorMessage);
            } else {
                System.setProperty(mockserverSocksProxy, hostAndPort);
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private static InetSocketAddress readInetSocketAddressProperty(String key, String environmentVariableKey) {
        InetSocketAddress inetSocketAddress = null;
        String proxy = readPropertyHierarchically(key, environmentVariableKey, null);
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

    @SuppressWarnings("unused")
    private static List<Integer> readIntegerListProperty(String key, String environmentVariableKey, Integer defaultValue) {
        try {
            return INTEGER_STRING_LIST_PARSER.toList(readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return Collections.emptyList();
        }
    }

    private static Integer readIntegerProperty(String key, String environmentVariableKey, int defaultValue) {
        try {
            return Integer.parseInt(readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, String environmentVariableKey, long defaultValue) {
        try {
            return Long.parseLong(readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
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
            if (MOCK_SERVER_LOGGER != null && MockServerLogger.isEnabled(Level.INFO)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(Level.INFO)
                        .setMessageFormat(propertiesLogDump.toString())
                );
            }
        }

        return properties;
    }

    @SuppressWarnings("ConstantConditions")
    private static String readPropertyHierarchically(String systemPropertyKey, String environmentVariableKey, String defaultValue) {
        if (isBlank(environmentVariableKey)) {
            throw new IllegalArgumentException("environment property name cannot be null for " + systemPropertyKey);
        }
        String defaultOrEnvironmentVariable = isBlank(System.getenv(environmentVariableKey)) ?
            defaultValue :
            System.getenv(environmentVariableKey);
        String propertyValue = System.getProperty(systemPropertyKey, PROPERTIES != null ? PROPERTIES.getProperty(systemPropertyKey, defaultOrEnvironmentVariable) : defaultOrEnvironmentVariable);
        if (propertyValue != null && propertyValue.startsWith("\"") && propertyValue.endsWith("\"")) {
            propertyValue = propertyValue.replaceAll("^\"|\"$", "");
        }
        return propertyValue;
    }
}
