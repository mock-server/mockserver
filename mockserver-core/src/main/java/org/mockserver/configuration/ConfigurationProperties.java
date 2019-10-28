package org.mockserver.configuration;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.slf4j.event.Level;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.logging.MockServerLogger.configureLogger;

/**
 * @author jamesdbloom
 */
public class ConfigurationProperties {

    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final long DEFAULT_MAX_TIMEOUT = 20;
    private static final int DEFAULT_CONNECT_TIMEOUT = 20000;
    private static final int DEFAULT_MAX_EXPECTATIONS = 5000;
    private static final int DEFAULT_MAX_WEB_SOCKET_EXPECTATIONS = 1000;
    private static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_HEADER_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_CHUNK_SIZE = Integer.MAX_VALUE;
    private static final int DEFAULT_NIO_EVENT_LOOP_THREAD_COUNT = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 5));
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "org/mockserver/socket/CertificateAuthorityPrivateKey.pem";
    private static final String DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "org/mockserver/socket/CertificateAuthorityCertificate.pem";

    private static final String MOCKSERVER_PROPERTY_FILE = "mockserver.propertyFile";
    private static final String MOCKSERVER_ENABLE_CORSFOR_API = "mockserver.enableCORSForAPI";
    private static final String MOCKSERVER_ENABLE_CORSFOR_ALL_RESPONSES = "mockserver.enableCORSForAllResponses";
    private static final String MOCKSERVER_MAX_EXPECTATIONS = "mockserver.maxExpectations";
    private static final String MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS = "mockserver.maxWebSocketExpectations";
    private static final String MOCKSERVER_MAX_INITIAL_LINE_LENGTH = "mockserver.maxInitialLineLength";
    private static final String MOCKSERVER_MAX_HEADER_SIZE = "mockserver.maxHeaderSize";
    private static final String MOCKSERVER_MAX_CHUNK_SIZE = "mockserver.maxChunkSize";
    private static final String MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT = "mockserver.nioEventLoopThreadCount";
    private static final String MOCKSERVER_MAX_SOCKET_TIMEOUT = "mockserver.maxSocketTimeout";
    private static final String MOCKSERVER_SOCKET_CONNECTION_TIMEOUT = "mockserver.socketConnectionTimeout";
    private static final String MOCKSERVER_JAVA_KEY_STORE_FILE_PATH = "mockserver.javaKeyStoreFilePath";
    private static final String MOCKSERVER_JAVA_KEY_STORE_PASSWORD = "mockserver.javaKeyStorePassword";
    private static final String MOCKSERVER_JAVA_KEY_STORE_TYPE = "mockserver.javaKeyStoreType";
    private static final String MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT = "mockserver.deleteGeneratedKeyStoreOnExit";
    private static final String MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME = "mockserver.sslCertificateDomainName";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS = "mockserver.sslSubjectAlternativeNameDomains";
    private static final String MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS = "mockserver.sslSubjectAlternativeNameIps";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY = "mockserver.certificateAuthorityPrivateKey";
    private static final String MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE = "mockserver.certificateAuthorityCertificate";
    private static final String MOCKSERVER_LOG_LEVEL = "mockserver.logLevel";
    private static final String MOCKSERVER_DISABLE_REQUEST_AUDIT = "mockserver.disableRequestAudit";
    private static final String MOCKSERVER_DISABLE_SYSTEM_OUT = "mockserver.disableSystemOut";
    private static final String MOCKSERVER_HTTP_PROXY = "mockserver.httpProxy";
    private static final String MOCKSERVER_HTTPS_PROXY = "mockserver.httpsProxy";
    private static final String MOCKSERVER_SOCKS_PROXY = "mockserver.socksProxy";
    private static final String MOCKSERVER_LOCAL_BOUND_IP = "mockserver.localBoundIP";
    private static final String MOCKSERVER_HTTP_PROXY_SERVER_REALM = "mockserver.proxyAuthenticationRealm";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_USERNAME = "mockserver.proxyAuthenticationUsername";
    private static final String MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD = "mockserver.proxyAuthenticationPassword";
    private static final String MOCKSERVER_INITIALIZATION_CLASS = "mockserver.initializationClass";
    private static final String MOCKSERVER_INITIALIZATION_JSON_PATH = "mockserver.initializationJsonPath";

    private static final Properties PROPERTIES = readPropertyFile();
    private static final Set<String> ALL_SUBJECT_ALTERNATIVE_DOMAINS = Sets.newConcurrentHashSet();
    private static final Set<String> ALL_SUBJECT_ALTERNATIVE_IPS = Sets.newConcurrentHashSet();
    private static final AtomicBoolean REBUILD_KEY_STORE = new AtomicBoolean(false);
    private static final IntegerStringListParser INTEGER_STRING_LIST_PARSER = new IntegerStringListParser();

    static {
        addSslSubjectAlternativeNameDomains(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_DOMAINS, "localhost").split(","));
        addSslSubjectAlternativeNameIps(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "127.0.0.1,0.0.0.0").split(","));
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
                .put("OFF", "OFF")
                .build();
        }
        return slf4jOrJavaLoggerToSLF4JLevelMapping;
    }

    private static String propertyFile() {
        return System.getProperty(MOCKSERVER_PROPERTY_FILE, "mockserver.properties");
    }

    public static boolean enableCORSForAPI() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORSFOR_API, "" + true));
    }

    public static boolean enableCORSForAPIHasBeenSetExplicitly() {
        return (
            System.getProperty(MOCKSERVER_ENABLE_CORSFOR_API) != null ||
                PROPERTIES.getProperty(MOCKSERVER_ENABLE_CORSFOR_API) != null
        );
    }

    public static void enableCORSForAPI(boolean enableCORSForAPI) {
        System.setProperty(MOCKSERVER_ENABLE_CORSFOR_API, "" + enableCORSForAPI);
    }

    public static boolean enableCORSForAllResponses() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_ENABLE_CORSFOR_ALL_RESPONSES, "" + false));
    }

    public static void enableCORSForAllResponses(boolean enableCORSForAllResponses) {
        System.setProperty(MOCKSERVER_ENABLE_CORSFOR_ALL_RESPONSES, "" + enableCORSForAllResponses);
    }

    public static int maxExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_EXPECTATIONS, DEFAULT_MAX_EXPECTATIONS);
    }

    public static void maxExpectations(int count) {
        System.setProperty(MOCKSERVER_MAX_EXPECTATIONS, "" + count);
    }

    public static int requestLogSize() {
        return readIntegerProperty(MOCKSERVER_MAX_EXPECTATIONS, maxExpectations() * maxExpectations());
    }

    public static void requestLogSize(int count) {
        System.setProperty(MOCKSERVER_MAX_EXPECTATIONS, "" + count);
    }

    public static int maxWebSocketExpectations() {
        return readIntegerProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, DEFAULT_MAX_WEB_SOCKET_EXPECTATIONS);
    }

    public static void maxWebSocketExpectations(int count) {
        System.setProperty(MOCKSERVER_MAX_WEB_SOCKET_EXPECTATIONS, "" + count);
    }

    public static int maxInitialLineLength() {
        return readIntegerProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_INITIAL_LINE_LENGTH);
    }

    public static void maxInitialLineLength(int length) {
        System.setProperty(MOCKSERVER_MAX_INITIAL_LINE_LENGTH, "" + length);
    }

    public static int maxHeaderSize() {
        return readIntegerProperty(MOCKSERVER_MAX_HEADER_SIZE, DEFAULT_MAX_HEADER_SIZE);
    }

    public static void maxHeaderSize(int size) {
        System.setProperty(MOCKSERVER_MAX_HEADER_SIZE, "" + size);
    }

    public static int maxChunkSize() {
        return readIntegerProperty(MOCKSERVER_MAX_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE);
    }

    public static void maxChunkSize(int size) {
        System.setProperty(MOCKSERVER_MAX_CHUNK_SIZE, "" + size);
    }

    public static int nioEventLoopThreadCount() {
        return readIntegerProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, DEFAULT_NIO_EVENT_LOOP_THREAD_COUNT);
    }

    public static void nioEventLoopThreadCount(int count) {
        System.setProperty(MOCKSERVER_NIO_EVENT_LOOP_THREAD_COUNT, "" + count);
    }

    public static long maxSocketTimeout() {
        return readLongProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, TimeUnit.SECONDS.toMillis(DEFAULT_MAX_TIMEOUT));
    }

    public static void maxSocketTimeout(long milliseconds) {
        System.setProperty(MOCKSERVER_MAX_SOCKET_TIMEOUT, "" + milliseconds);
    }

    public static int socketConnectionTimeout() {
        return readIntegerProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    public static void socketConnectionTimeout(int milliseconds) {
        System.setProperty(MOCKSERVER_SOCKET_CONNECTION_TIMEOUT, "" + milliseconds);
    }

    public static String javaKeyStoreFilePath() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_FILE_PATH, KeyStoreFactory.defaultKeyStoreFileName());
    }

    public static void javaKeyStoreFilePath(String keyStoreFilePath) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_FILE_PATH, keyStoreFilePath);
        rebuildKeyStore(true);
    }

    public static String javaKeyStorePassword() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_PASSWORD, KeyStoreFactory.KEY_STORE_PASSWORD);
    }

    public static void javaKeyStorePassword(String keyStorePassword) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_PASSWORD, keyStorePassword);
        rebuildKeyStore(true);
    }

    public static String javaKeyStoreType() {
        return readPropertyHierarchically(MOCKSERVER_JAVA_KEY_STORE_TYPE, KeyStore.getDefaultType());
    }

    public static void javaKeyStoreType(String keyStoreType) {
        System.setProperty(MOCKSERVER_JAVA_KEY_STORE_TYPE, keyStoreType);
        rebuildKeyStore(true);
    }

    public static boolean deleteGeneratedKeyStoreOnExit() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT, "" + true));
    }

    public static void deleteGeneratedKeyStoreOnExit(boolean deleteGeneratedKeyStoreOnExit) {
        System.setProperty(MOCKSERVER_DELETE_GENERATED_KEY_STORE_ON_EXIT, "" + deleteGeneratedKeyStoreOnExit);
        rebuildKeyStore(true);
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, KeyStoreFactory.CERTIFICATE_DOMAIN);
    }

    public static void sslCertificateDomainName(String domainName) {
        System.setProperty(MOCKSERVER_SSL_CERTIFICATE_DOMAIN_NAME, domainName);
        rebuildKeyStore(true);
    }

    public static String[] sslSubjectAlternativeNameDomains() {
        return ALL_SUBJECT_ALTERNATIVE_DOMAINS.toArray(new String[ALL_SUBJECT_ALTERNATIVE_DOMAINS.size()]);
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
            rebuildKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameDomains() {
        ALL_SUBJECT_ALTERNATIVE_DOMAINS.clear();
    }

    public static boolean containsSslSubjectAlternativeName(String domainOrIp) {
        return ALL_SUBJECT_ALTERNATIVE_DOMAINS.contains(domainOrIp) || ALL_SUBJECT_ALTERNATIVE_IPS.contains(domainOrIp);
    }

    public static String[] sslSubjectAlternativeNameIps() {
        return ALL_SUBJECT_ALTERNATIVE_IPS.toArray(new String[ALL_SUBJECT_ALTERNATIVE_IPS.size()]);
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
            rebuildKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameIps() {
        ALL_SUBJECT_ALTERNATIVE_IPS.clear();
        addSslSubjectAlternativeNameIps(readPropertyHierarchically(MOCKSERVER_SSL_SUBJECT_ALTERNATIVE_NAME_IPS, "127.0.0.1,0.0.0.0").split(","));
    }

    public static boolean rebuildKeyStore() {
        return REBUILD_KEY_STORE.get();
    }

    public static void rebuildKeyStore(boolean rebuildKeyStore) {
        ConfigurationProperties.REBUILD_KEY_STORE.set(rebuildKeyStore);
    }

    public static String certificateAuthorityPrivateKey() {
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY, DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
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
        return readPropertyHierarchically(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
    }

    /**
     * Override the default certificate authority X509 certificate
     *
     * @param certificateAuthorityCertificate location of the PEM file containing the certificate authority X509 certificate
     */
    public static void certificateAuthorityCertificate(String certificateAuthorityCertificate) {
        System.setProperty(MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE, certificateAuthorityCertificate);
    }

    public static Level logLevel() {
        ;
        return Level.valueOf(getSLF4JOrJavaLoggerToSLF4JLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, DEFAULT_LOG_LEVEL).toUpperCase()));
    }

    public static String javaLoggerLogLevel() {
        ;
        return getSLF4JOrJavaLoggerToJavaLoggerLevelMapping().get(readPropertyHierarchically(MOCKSERVER_LOG_LEVEL, DEFAULT_LOG_LEVEL).toUpperCase());
    }

    /**
     * Override the default logging level of INFO
     *
     * @param level the log level, which can be TRACE, DEBUG, INFO, WARN, ERROR, OFF, FINEST, FINE, INFO, WARNING, SEVERE
     */
    public static void logLevel(String level) {
        if (!getSLF4JOrJavaLoggerToSLF4JLevelMapping().containsKey(level)) {
            throw new IllegalArgumentException("log level \"" + level + "\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\" or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\"");
        }
        System.setProperty(MOCKSERVER_LOG_LEVEL, level);
        configureLogger();
    }

    public static boolean disableRequestAudit() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_REQUEST_AUDIT, "" + false));
    }

    public static void disableRequestAudit(boolean disableRequestAudit) {
        System.setProperty(MOCKSERVER_DISABLE_REQUEST_AUDIT, "" + disableRequestAudit);
    }

    public static boolean disableSystemOut() {
        return Boolean.parseBoolean(readPropertyHierarchically(MOCKSERVER_DISABLE_SYSTEM_OUT, "" + false));
    }

    public static void disableSystemOut(boolean disableSystemOut) {
        System.setProperty(MOCKSERVER_DISABLE_SYSTEM_OUT, "" + disableSystemOut);
    }

    public static InetSocketAddress httpProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_HTTP_PROXY);
    }

    public static void httpProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "httpProxy", MOCKSERVER_HTTP_PROXY);
    }

    public static InetSocketAddress httpsProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_HTTPS_PROXY);
    }

    public static void httpsProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "httpsProxy", MOCKSERVER_HTTPS_PROXY);
    }

    public static InetSocketAddress socksProxy() {
        return readInetSocketAddressProperty(MOCKSERVER_SOCKS_PROXY);
    }

    public static void socksProxy(String hostAndPort) {
        validateHostAndPort(hostAndPort, "socksProxy", MOCKSERVER_SOCKS_PROXY);
    }

    public static String localBoundIP() {
        return readPropertyHierarchically(MOCKSERVER_LOCAL_BOUND_IP, "");
    }

    public static void localBoundIP(String localBoundIP) {
        System.setProperty(MOCKSERVER_LOCAL_BOUND_IP, InetAddresses.forString(localBoundIP).getHostAddress());
    }

    public static String proxyAuthenticationRealm() {
        return readPropertyHierarchically(MOCKSERVER_HTTP_PROXY_SERVER_REALM, "MockServer HTTP Proxy");
    }

    public static void proxyAuthenticationRealm(String proxyAuthenticationRealm) {
        System.setProperty(MOCKSERVER_HTTP_PROXY_SERVER_REALM, proxyAuthenticationRealm);
    }

    public static String proxyAuthenticationUsername() {
        return readPropertyHierarchically(MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, "");
    }

    public static void proxyAuthenticationUsername(String proxyAuthenticationUsername) {
        System.setProperty(MOCKSERVER_PROXY_AUTHENTICATION_USERNAME, proxyAuthenticationUsername);
    }

    public static String proxyAuthenticationPassword() {
        return readPropertyHierarchically(MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, "");
    }

    public static void proxyAuthenticationPassword(String proxyAuthenticationPassword) {
        System.setProperty(MOCKSERVER_PROXY_AUTHENTICATION_PASSWORD, proxyAuthenticationPassword);
    }

    public static String initializationClass() {
        return readPropertyHierarchically(MOCKSERVER_INITIALIZATION_CLASS, "");
    }

    public static void initializationClass(String initializationClass) {
        System.setProperty(MOCKSERVER_INITIALIZATION_CLASS, initializationClass);
    }

    public static String initializationJsonPath() {
        return readPropertyHierarchically(MOCKSERVER_INITIALIZATION_JSON_PATH, "");
    }

    public static void initializationJsonPath(String initializationJsonPath) {
        System.setProperty(MOCKSERVER_INITIALIZATION_JSON_PATH, initializationJsonPath);
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

    private static InetSocketAddress readInetSocketAddressProperty(String key) {
        InetSocketAddress inetSocketAddress = null;
        String proxy = readPropertyHierarchically(key, null);
        if (isNotBlank(proxy)) {
            String[] proxyParts = proxy.split(":");
            if (proxyParts.length > 1) {
                try {
                    inetSocketAddress = new InetSocketAddress(proxyParts[0], Integer.parseInt(proxyParts[1]));
                } catch (NumberFormatException nfe) {
                    MockServerLogger.MOCK_SERVER_LOGGER.error("NumberFormatException converting value \"" + proxyParts[1] + "\" into an integer", nfe);
                }
            }
        }
        return inetSocketAddress;
    }

    private static List<Integer> readIntegerListProperty(String key, Integer defaultValue) {
        try {
            return INTEGER_STRING_LIST_PARSER.toList(readPropertyHierarchically(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MockServerLogger.MOCK_SERVER_LOGGER.error("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, "" + defaultValue) + "]", nfe);
            return Collections.emptyList();
        }
    }

    private static Integer readIntegerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(readPropertyHierarchically(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MockServerLogger.MOCK_SERVER_LOGGER.error("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, "" + defaultValue) + "]", nfe);
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(readPropertyHierarchically(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            MockServerLogger.MOCK_SERVER_LOGGER.error("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, "" + defaultValue) + "]", nfe);
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
                    if (MockServerLogger.MOCK_SERVER_LOGGER != null) {
                        MockServerLogger.MOCK_SERVER_LOGGER.error("Exception loading property file [" + propertyFile() + "]", e);
                    }
                }
            } else {
                if (MockServerLogger.MOCK_SERVER_LOGGER != null) {
                    MockServerLogger.MOCK_SERVER_LOGGER.debug(SERVER_CONFIGURATION, "Property file not found on classpath using path [" + propertyFile() + "]");
                }
                try {
                    properties.load(new FileInputStream(propertyFile()));
                } catch (FileNotFoundException e) {
                    if (MockServerLogger.MOCK_SERVER_LOGGER != null) {
                        MockServerLogger.MOCK_SERVER_LOGGER.debug(SERVER_CONFIGURATION, "Property file not found using path [" + propertyFile() + "]");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (MockServerLogger.MOCK_SERVER_LOGGER != null) {
                        MockServerLogger.MOCK_SERVER_LOGGER.error("Exception loading property file [" + propertyFile() + "]", e);
                    }
                }
            }
        } catch (IOException ioe) {
            // ignore
        }

        if (!properties.isEmpty()) {
            Enumeration<?> propertyNames = properties.propertyNames();

            StringBuilder propertiesLogDump = new StringBuilder();
            propertiesLogDump.append("Reading properties from property file [").append(propertyFile()).append("]:" + NEW_LINE);
            while (propertyNames.hasMoreElements()) {
                String propertyName = String.valueOf(propertyNames.nextElement());
                propertiesLogDump.append("\t").append(propertyName).append(" = ").append(properties.getProperty(propertyName)).append(NEW_LINE);
            }
            if (MockServerLogger.MOCK_SERVER_LOGGER != null) {
                MockServerLogger.MOCK_SERVER_LOGGER.info(SERVER_CONFIGURATION, propertiesLogDump.toString());
            }
        }

        return properties;
    }

    private static String readPropertyHierarchically(String key, String defaultValue) {
        return System.getProperty(key, PROPERTIES != null ? PROPERTIES.getProperty(key, defaultValue) : defaultValue);
    }
}
