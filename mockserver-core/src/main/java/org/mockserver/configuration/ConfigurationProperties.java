package org.mockserver.configuration;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jamesdbloom
 */
public class ConfigurationProperties {

    static final long DEFAULT_MAX_TIMEOUT = 120;
    static final Logger logger = LoggerFactory.getLogger(ConfigurationProperties.class);
    static final Properties PROPERTIES = readPropertyFile();

    static final Set<String> allSubjectAlternativeDomains = Sets.newConcurrentHashSet();
    static final Set<String> allSubjectAlternativeIps = Sets.newConcurrentHashSet();
    static final AtomicBoolean rebuildKeyStore = new AtomicBoolean(false);

    static {
        addSslSubjectAlternativeNameDomains(readPropertyHierarchically("mockserver.sslSubjectAlternativeNameDomains", "localhost").split(","));
        addSslSubjectAlternativeNameIps(readPropertyHierarchically("mockserver.sslSubjectAlternativeNameIps", "127.0.0.1,0.0.0.0").split(","));
    }

    // property file config
    public static String propertyFile() {
        return System.getProperty("mockserver.propertyFile", "mockserver.properties");
    }

    // socket config
    public static long maxSocketTimeout() {
        return readLongProperty("mockserver.maxSocketTimeout", TimeUnit.SECONDS.toMillis(DEFAULT_MAX_TIMEOUT));
    }

    public static void maxSocketTimeout(long milliseconds) {
        System.setProperty("mockserver.maxSocketTimeout", "" + milliseconds);
    }

    // ssl config
    public static String javaKeyStoreFilePath() {
        return readPropertyHierarchically("mockserver.javaKeyStoreFilePath", SSLFactory.defaultKeyStoreFileName());
    }

    public static void javaKeyStoreFilePath(String keyStoreFilePath) {
        System.setProperty("mockserver.javaKeyStoreFilePath", keyStoreFilePath);
        rebuildKeyStore(true);
    }

    public static String javaKeyStorePassword() {
        return readPropertyHierarchically("mockserver.javaKeyStorePassword", SSLFactory.KEY_STORE_PASSWORD);
    }

    public static void javaKeyStorePassword(String keyStorePassword) {
        System.setProperty("mockserver.javaKeyStorePassword", keyStorePassword);
        rebuildKeyStore(true);
    }

    public static String javaKeyStoreType() {
        return readPropertyHierarchically("mockserver.javaKeyStoreType", KeyStore.getDefaultType());
    }

    public static void javaKeyStoreType(String keyStoreType) {
        System.setProperty("mockserver.javaKeyStoreType", keyStoreType);
        rebuildKeyStore(true);
    }

    public static boolean deleteGeneratedKeyStoreOnExit() {
        return Boolean.parseBoolean(readPropertyHierarchically("mockserver.deleteGeneratedKeyStoreOnExit", "" + true));
    }

    public static void deleteGeneratedKeyStoreOnExit(boolean deleteGeneratedKeyStoreOnExit) {
        System.setProperty("mockserver.deleteGeneratedKeyStoreOnExit", "" + deleteGeneratedKeyStoreOnExit);
        rebuildKeyStore(true);
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically("mockserver.sslCertificateDomainName", SSLFactory.CERTIFICATE_DOMAIN);
    }

    public static void sslCertificateDomainName(String domainName) {
        System.setProperty("mockserver.sslCertificateDomainName", domainName);
        rebuildKeyStore(true);
    }

    public static String[] sslSubjectAlternativeNameDomains() {
        return allSubjectAlternativeDomains.toArray(new String[allSubjectAlternativeDomains.size()]);
    }

    public static void addSslSubjectAlternativeNameDomains(String... newSubjectAlternativeNameDomains) {
        boolean subjectAlternativeDomainsModified = false;
        for (String subjectAlternativeDomain : newSubjectAlternativeNameDomains) {
            if (allSubjectAlternativeDomains.add(subjectAlternativeDomain.trim())) {
                subjectAlternativeDomainsModified = true;
            }
        }
        if (subjectAlternativeDomainsModified) {
            System.setProperty("mockserver.sslSubjectAlternativeNameDomains", Joiner.on(",").join(new TreeSet(allSubjectAlternativeDomains)));
            rebuildKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameDomains() {
        allSubjectAlternativeDomains.clear();
        addSslSubjectAlternativeNameDomains(readPropertyHierarchically("mockserver.sslSubjectAlternativeNameDomains", "localhost").split(","));
    }

    public static boolean containsSslSubjectAlternativeName(String domainOrIp) {
        return allSubjectAlternativeDomains.contains(domainOrIp) || allSubjectAlternativeIps.contains(domainOrIp);
    }

    public static String[] sslSubjectAlternativeNameIps() {
        return allSubjectAlternativeIps.toArray(new String[allSubjectAlternativeIps.size()]);
    }

    public static void addSslSubjectAlternativeNameIps(String... newSubjectAlternativeNameIps) {
        boolean subjectAlternativeIpsModified = false;
        for (String subjectAlternativeDomain : newSubjectAlternativeNameIps) {
            if (allSubjectAlternativeIps.add(subjectAlternativeDomain.trim())) {
                subjectAlternativeIpsModified = true;
            }
        }
        if (subjectAlternativeIpsModified) {
            System.setProperty("mockserver.sslSubjectAlternativeNameIps", Joiner.on(",").join(new TreeSet(allSubjectAlternativeIps)));
            rebuildKeyStore(true);
        }
    }

    public static void clearSslSubjectAlternativeNameIps() {
        allSubjectAlternativeIps.clear();
        addSslSubjectAlternativeNameIps(readPropertyHierarchically("mockserver.sslSubjectAlternativeNameIps", "127.0.0.1,0.0.0.0").split(","));
    }

    public static boolean rebuildKeyStore() {
        return rebuildKeyStore.get();
    }

    public static void rebuildKeyStore(boolean rebuildKeyStore) {
        ConfigurationProperties.rebuildKeyStore.set(rebuildKeyStore);
    }

    // mockserver config
    public static int mockServerPort() {
        return readIntegerProperty("mockserver.mockServerPort", -1);
    }

    public static void mockServerPort(int port) {
        System.setProperty("mockserver.mockServerPort", "" + port);
    }

    // proxy config
    public static int proxyPort() {
        return readIntegerProperty("mockserver.proxyPort", -1);
    }

    public static void proxyPort(int port) {
        System.setProperty("mockserver.proxyPort", "" + port);
    }

    private static Integer readIntegerProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(readPropertyHierarchically(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, "" + defaultValue) + "]", nfe);
            return defaultValue;
        }
    }

    private static Long readLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(readPropertyHierarchically(key, "" + defaultValue));
        } catch (NumberFormatException nfe) {
            logger.error("NumberFormatException converting " + key + " with value [" + readPropertyHierarchically(key, "" + defaultValue) + "]", nfe);
            return defaultValue;
        }
    }

    public static Properties readPropertyFile() {

        Properties properties = new Properties();

        InputStream inputStream = ConfigurationProperties.class.getClassLoader().getResourceAsStream(propertyFile());
        if (inputStream != null) {
            try {
                properties.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("Exception loading property file [" + propertyFile() + "]", e);
            }
        } else {
            logger.debug("Property file not found on classpath using path [" + propertyFile() + "]");
            try {
                properties.load(new FileInputStream(propertyFile()));
            } catch (FileNotFoundException e) {
                logger.debug("Property file not found using path [" + propertyFile() + "]");
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("Exception loading property file [" + propertyFile() + "]", e);
            }
        }

        if (!properties.isEmpty()) {
            IOUtils.closeQuietly(inputStream);
            Enumeration<?> propertyNames = properties.propertyNames();

            StringBuilder propertiesLogDump = new StringBuilder();
            propertiesLogDump.append("Reading properties from property file [").append(propertyFile()).append("]:\n");
            while (propertyNames.hasMoreElements()) {
                String propertyName = String.valueOf(propertyNames.nextElement());
                propertiesLogDump.append("\t").append(propertyName).append(" = ").append(properties.getProperty(propertyName)).append("\n");
            }
            logger.info(propertiesLogDump.toString());
        }

        return properties;
    }

    public static String readPropertyHierarchically(String key, String defaultValue) {
        return System.getProperty(key, PROPERTIES.getProperty(key, defaultValue));
    }

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static void overrideLogLevel(String level) {
        if (level != null) {
            if (!Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF").contains(level)) {
                throw new IllegalArgumentException("log level \"" + level + "\" is not legal it must be one of \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\"");
            }
            System.setProperty("mockserver.logLevel", level);
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level);
            overrideLogLevelWithReflection(level, "org.mockserver");
            overrideLogLevelWithReflection(level, "org.mockserver.mockserver");
            overrideLogLevelWithReflection(level, "org.mockserver.mockserver.MockServerHandler");
            overrideLogLevelWithReflection(level, "org.mockserver.proxy");
            overrideLogLevelWithReflection(level, "org.mockserver.proxy.http.HttpProxyHandler");
            overrideLogLevelWithReflection(level, "org.mockserver.matchers.HttpRequestMatcher");
            overrideLogLevelWithReflection(level, "org.mockserver.filters.LogFilter");
        }
    }

    private static void overrideLogLevelWithReflection(String level, String loggerName) {
        Logger logger = LoggerFactory.getLogger(loggerName);

        try {
            // check if logback-classic is being used
            Class logbackLevelClass = ConfigurationProperties.class.getClassLoader().loadClass("ch.qos.logback.classic.Level");

            // convert string to log level
            Method toLevelMethod = logbackLevelClass.getDeclaredMethod("toLevel", String.class);
            toLevelMethod.setAccessible(true);
            Object levelInstance = toLevelMethod.invoke(logbackLevelClass, level);

            // update log level
            Method setLevelMethod = logger.getClass().getDeclaredMethod("setLevel", logbackLevelClass);
            if (setLevelMethod != null) {
                setLevelMethod.invoke(logger, levelInstance);
            }
        } catch (Exception e) {
            ConfigurationProperties.logger.debug("Exception updating logging level using reflection, likely cause is Logback is not on the classpath");
        }


        try {
            // check if SimpleLogger is used (i.e. in maven plugin)
            Class loggerClass = logger.getClass();
            if (logger.getClass().getName().equals("org.slf4j.impl.SimpleLogger")) {
                // convert string to log level
                Method stringToLevelMethod = loggerClass.getDeclaredMethod("stringToLevel", String.class);
                stringToLevelMethod.setAccessible(true);
                Object logLevelInstance = stringToLevelMethod.invoke(logger, level);

                // update log level
                Field currentLogLevelField = loggerClass.getDeclaredField("currentLogLevel");
                currentLogLevelField.setAccessible(true);
                currentLogLevelField.set(logger, logLevelInstance);
            }
        } catch (Exception e) {
            ConfigurationProperties.logger.debug("Exception updating logging level using reflection, likely cause is Logback is not on the classpath");
        }
    }


}
