package org.mockserver.configuration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.slf4j.event.Level;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
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
    private static final int DEFAULT_MAX_FUTURE_TIMEOUT = 60;
    private static final int DEFAULT_MAX_EXPECTATIONS = 5000;
    private static final int DEFAULT_MAX_WEB_SOCKET_EXPECTATIONS = 1500;
    private static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_HEADER_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_CHUNK_SIZE = Integer.MAX_VALUE;
    private static final String DEFAULT_ENABLE_CORS_FOR_API = "false";
    private static final String DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES = "false";
    private static final String DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE = "false";
    private static final int DEFAULT_NIO_EVENT_LOOP_THREAD_COUNT = Math.max(20, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors()));
    private static final int DEFAULT_ACTION_HANDLER_THREAD_COUNT = Math.max(20, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT = 5;
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "org/mockserver/socket/CertificateAuthorityPrivateKey.pem";
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "org/mockserver/socket/CertificateAuthorityCertificate.pem";
    private static final String DEFAULT_CORS_ALLOW_HEADERS = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization";
    private static final String DEFAULT_CORS_ALLOW_METHODS = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE";
    private static final String DEFAULT_CORS_ALLOW_CREDENTIALS = "true";
    private static final int DEFAULT_CORS_MAX_AGE_IN_SECONDS = 300;

    private static final String MOCKSERVER_PROPERTY_FILE = "mockserver.propertyFile";
    private static final String MOCKSERVER_ENABLE_CORS_FOR_API = "mockserver.enableCORSForAPI";
    private static final String MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES = "mockserver.enableCORSForAllResponses";
    private static final String MOCKSERVER_MAX_EXPECTATIONS = "mockserver.maxExpectations";
    private static final String MOCKSERVER_MAX_LOG_ENTRIES = "mockserver.maxLogEntries";
    private static final String MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS = "mockserver.maxWebSocketExpectations";
    private static final String MOCKSERVER_MAX_INITIAL_LINE_LENGTH = "mockserver.maxInitialLineLength";
    private static final String MOCKSERVER_MAX_HEADER_SIZE = "mockserver.maxHeaderSize";
    private static final String MOCKSERVER_MAX_CHUNK_SIZE = "mockserver.maxChunkSize";
    private static final String MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.nioEventLoopThreadCount";
    private static final String MOCKSERVER_ACTION_HANDLER_THREAD_COUNT = "mockserver.actionHandlerThreadCount";
    private static final String MOCKSERVER_WEB_SOCKET_CLIENT_EVENT_LOOP_THREAD_COUNT = "mockserver.webSocketClientEventLoopThreadCount";
    private static final String MOCKSERVER_MAX_SOCKET_TIMEOUT = "mockserver.maxSocketTimeout";
    private static final String MOCKSERVER_MAX_FUTURE_TIMEOUT = "mockserver.maxFutureTimeout";
    private static final String MOCKSERVER_SOCKET_CONNECTION_TIMEOUT = "mockserver.socketConnectionTimeout";
    private static final String MOCKSERVER_JAVA_KEY_STORE_FILE_PATH = "mockserver.javaKeyStoreFilePath";
    private static final String MOCKSERVER_JAVA_KEY_STORE_PASSWORD = "mockserver.javaKeyStorePassword";
    private static final String MOCKSERVER_JAVA_KEY_STORE_TYPE = "mockserver.javaKeyStoreType";
    private static final String MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT = "mockserver.deleteGeneratedKeyStoreOnExit";
    private static final String MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME = "mockserver.sslCertificateDomainName";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS = "mockserver.sslSubjectAlternativeNameDomains";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS = "mockserver.sslSubjectAlternativeNameIps";
    private static final String MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE = "mockserver.preventCertificateDynamicUpdate";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "mockserver.certificateAuthorityPrivateKey";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "mockserver.certificateAuthorityCertificate";
    private static final String MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE = "mockserver.directoryToSaveDynamicSSLCertificate";
    private static final String MOCKSERVER_LOG_LEVEL = "mockserver.logLevel";
    private static final String MOCKSERVER_METRICS_ENABLED = "mockserver.metricsEnabled";
    private static final String MOCKSERVER_DISABLE_SYSTEM_OUT = "mockserver.disableSystemOut";
    private static final String MOCKSERVER_LOCAL_BOUND_IP = "mockserver.localBoundIP";
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

    private static Level logLevel = Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase()));
    private static String javaLoggerLogLevel = getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, "MOCKSERVER_LOG_LEVEL", DEFAULT_LOG_LEVEL).toUpperCase());
    private static boolean metricsEnabled = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
    private static boolean disableSystemOut = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
    private static boolean enableCORSForAPI = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", DEFAULT_ENABLE_CORS_FOR_API));
    private static boolean enableCORSForAPIHasBeenSetExplicitly = System.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null ||
        PROPERTIES.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null;
    private static boolean enableCORSForAllResponses = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES));
    private static int maxInitialLineLength = readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", DEFAULT_MAX_INITIAL_LINE_LENGTH);
    private static int maxHeaderSize = readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", DEFAULT_MAX_HEADER_SIZE);
    private static int maxChunkSize = readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", DEFAULT_MAX_CHUNK_SIZE);
    private static boolean preventCertificateDynamicUpdate = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE));

    @VisibleForTesting
    static void reset() {
        ALL_SUBJECT_ALTERNATIVE_DOMAINS.clear();
        ALL_SUBJECT_ALTERNATIVE_IPS.clear();
        REBUILD_KEY_STORE.set(false);
        REBUILD_SERVER_KEY_STORE.set(false);
        logLevel = Level.valueOf(DEFAULT_LOG_LEVEL);
        javaLoggerLogLevel = DEFAULT_LOG_LEVEL;
        metricsEnabled = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_METRICS_ENABLED, "MOCKSERVER_METRICS_ENABLED", "" + false));
        disableSystemOut = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "MOCKSERVER_DISABLE_SYSTEM_OUT", "" + false));
        enableCORSForAPI = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_API, "MOCKSERVER_ENABLE_CORS_FOR_API", DEFAULT_ENABLE_CORS_FOR_API));
        enableCORSForAPIHasBeenSetExplicitly = System.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null ||
            PROPERTIES.getProperty(MOCKSERVER_ENABLE_CORS_FOR_API) != null;
        enableCORSForAllResponses = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES, "MOCKSERVER_ENABLE_CORS_FOR_ALL_RESPONSES", DEFAULT_ENABLE_CORS_FOR_ALL_RESPONSES));
        maxInitialLineLength = readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "MOCKSERVER_MAX_INITIAL_LINE_LENGTH", DEFAULT_MAX_INITIAL_LINE_LENGTH);
        maxHeaderSize = readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, "MOCKSERVER_MAX_HEADER_SIZE", DEFAULT_MAX_HEADER_SIZE);
        maxChunkSize = readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, "MOCKSERVER_MAX_CHUNK_SIZE", DEFAULT_MAX_CHUNK_SIZE);
        preventCertificateDynamicUpdate = Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE, "MOCKSERVER_PREVENT_CERTIFICATE_DYNAMIC_UPDATE", DEFAULT_PREVENT_CERTIFICATE_DYNAMIC_UPDATE));
    }

    private static String propertyFile() {
        return System.getProperty(MOCKSERVER_PROPERTY_FILE, isBlank(System.getenv("MOCKSERVER_PROPERTY_FILE")) ? "mockserver.properties" : System.getenv("MOCKSERVER_PROPERTY_FILE"));
    }

    public static int maxExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_EXPECTATIONS, "MOCKSERVER_MAX_EXPECTATIONS", DEFAULT_MAX_EXPECTATIONS);
    }

    public static void maxExpectations(int count) {
        System.setProperty(MOCKSERVER_MAX_EXPECTATIONS, "" + count);
    }

    public static int maxLogEntries() {
        return readIntegerProperty(MOCKSERVER_MAX_LOG_ENTRIES, "MOCKSERVER_MAX_LOG_ENTRIES", maxExpectations() * maxExpectations());
    }

    public static void maxLogEntries(int count) {
        System.setProperty(MOCKSERVER_MAX_LOG_ENTRIES, "" + count);
    }

    public static int ringBufferSize() {
        return nextPowerOfTwo(maxExpectations() * maxExpectations());
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

    public static String javaKeyStoreFilePath() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_FILE_PATH, "MOCKSERVER_JAVA_KEY_STORE_FILE_PATH", KeyStoreFactory.defaultKeyStoreFileName());
    }

    public static void javaKeyStoreFilePath(String keyStoreFilePath) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_FILE_PATH, keyStoreFilePath);
        rebuildKeyStore(true);
    }

    public static String javaKeyStorePassword() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_PASSWORD, "MOCKSERVER_JAVA_KEY_STORE_PASSWORD", KeyStoreFactory.KEY_STORE_PASSWORD);
    }

    public static void javaKeyStorePassword(String keyStorePassword) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_PASSWORD, keyStorePassword);
        rebuildKeyStore(true);
    }

    public static String javaKeyStoreType() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_TYPE, "MOCKSERVER_JAVA_KEY_STORE_TYPE", KeyStore.getDefaultType());
    }

    public static void javaKeyStoreType(String keyStoreType) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_TYPE, keyStoreType);
        rebuildKeyStore(true);
    }

    public static boolean deleteGeneratedKeyStoreOnExit() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT, "MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT", "" + true));
    }

    public static void deleteGeneratedKeyStoreOnExit(boolean deleteGeneratedKeyStoreOnExit) {
        System.setProperty(MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT, "" + deleteGeneratedKeyStoreOnExit);
        rebuildKeyStore(true);
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, "MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME", KeyStoreFactory.CERTIFICATE_DOMAIN);
    }

    public static void sslCertificateDomainName(String domainName) {
        System.setProperty(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, domainName);
        rebuildServerKeyStore(true);
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
            rebuildServerKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameDomains() {
        ALL_SUBJECT_ALTERNATIVE_DOMAINS.clear();
    }

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
            rebuildServerKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameIps() {
        ALL_SUBJECT_ALTERNATIVE_IPS.clear();
        addSslSubjectAlternativeNameIps(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS", "127.0.0.1,0.0.0.0").split(","));
    }

    public static boolean rebuildKeyStore() {
        return REBUILD_KEY_STORE.get();
    }

    public static void rebuildKeyStore(boolean rebuildKeyStore) {
        ConfigurationProperties.REBUILD_KEY_STORE.set(rebuildKeyStore);
    }

    public static boolean rebuildServerKeyStore() {
        return REBUILD_SERVER_KEY_STORE.get();
    }

    public static void rebuildServerKeyStore(boolean rebuildKeyStore) {
        ConfigurationProperties.REBUILD_SERVER_KEY_STORE.set(rebuildKeyStore);
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
     * Override the default certificate authority private key
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
     * Override the default certificate authority X509 certificate
     *
     * @param certificateAuthorityCertificate location of the PEM file containing the certificate authority X509 certificate
     */
    public static void certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        System.setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, certificateAuthorityCertificate);
    }

    public static String directoryToSaveDynamicSSLCertificate() {
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, "MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE", "");
    }

    /**
     * Override the default location used to save dynamically generated certificates, by default this is saved as a temporary file by the JVM,
     * for example: /var/folders/lz/_kbrwxrx4ss3brnc0y9ms2vc0000gn/T/MockServerCertificate75d431bb-cbf1-4cfe-b8a2-000ece2150e3.pem1048371440427200504.tmp
     *
     * @param directoryToSaveDynamicSSLCertificate location to save private key and X509 certificate
     */
    public static void directoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        if (!new File(directoryToSaveDynamicSSLCertificate).exists()) {
            throw new RuntimeException(directoryToSaveDynamicSSLCertificate + " does not exist or is not accessible");
        }
        System.setProperty(MOCKSERVER_CERTIFICATE_DIRECTORY_TO_SAVE_DYNAMIC_SSL_CERTIFICATE, directoryToSaveDynamicSSLCertificate);
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
                throw new IllegalArgumentException("log level \"" + level + "\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\" or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\"");
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

    public static void localBoundIP(String localBoundIP) {
        System.setProperty(MOCKSERVER_LOCAL_BOUND_IP, InetAddresses.forString(localBoundIP).getHostAddress());
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
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("NumberFormatException converting value \"" + proxyParts[1] + "\" into an integer")
                            .setThrowable(nfe)
                    );
                }
            }
        }
        return inetSocketAddress;
    }

    private static List<Integer> readIntegerListProperty(String key, String environmentVariableKey, Integer defaultValue) {
        try {
            return INTEGER_STRING_LIST_PARSER.toList(readPropertyHierarchically(key, "", "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return Collections.emptyList();
        }
    }

    private static Integer readIntegerProperty(String key, String environmentVariableKey, int defaultValue) {
        try {
            return Integer.parseInt(readPropertyHierarchically(key, "", "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, String environmentVariableKey, long defaultValue) {
        try {
            return Long.parseLong(readPropertyHierarchically(key, "", "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, environmentVariableKey, "" + defaultValue) + "]")
                    .setThrowable(nfe)
            );
            return defaultValue;
        }
    }

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
                                .setType(LogEntry.LogMessageType.EXCEPTION)
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("exception loading property file [" + propertyFile() + "]")
                                .setThrowable(e)
                        );
                    }
                }
            } else {
                if (MOCK_SERVER_LOGGER != null) {
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
                    if (MOCK_SERVER_LOGGER != null) {
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
                                .setType(LogEntry.LogMessageType.EXCEPTION)
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
                propertiesLogDump.append("\t").append(propertyName).append(" = ").append(properties.getProperty(propertyName)).append(NEW_LINE);
            }
            if (MOCK_SERVER_LOGGER != null) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request())
                        .setMessageFormat(propertiesLogDump.toString())
                );
            }
        }

        return properties;
    }

    private static String readPropertyHierarchically(String systemPropertyKey, String environmentVariableKey, String defaultValue) {
        String defaultOrEnvironmentVariable = isBlank(System.getenv(environmentVariableKey)) ?
            defaultValue :
            System.getenv(environmentVariableKey);
        return System.getProperty(systemPropertyKey, PROPERTIES != null ? PROPERTIES.getProperty(systemPropertyKey, defaultOrEnvironmentVariable) : defaultOrEnvironmentVariable);
    }
}
