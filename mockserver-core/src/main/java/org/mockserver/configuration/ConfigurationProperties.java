package org.mockserver.configuration;

import com.google.common.base.Joiner;
import org.apache.commons.io.IOUtils;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ConfigurationProperties {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationProperties.class);
    static final long DEFAULT_MAX_TIMEOUT = 120;
    static final int DEFAULT_BUFFER_SIZE = 1024 * 1500;
    static final Properties PROPERTIES = readPropertyFile();

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
    }

    public static String javaKeyStorePassword() {
        return readPropertyHierarchically("mockserver.javaKeyStorePassword", SSLFactory.KEY_STORE_PASSWORD);
    }

    public static void javaKeyStorePassword(String keyStorePassword) {
        System.setProperty("mockserver.javaKeyStorePassword", keyStorePassword);
    }

    public static String javaKeyStoreType() {
        return readPropertyHierarchically("mockserver.javaKeyStoreType", KeyStore.getDefaultType());
    }

    public static void javaKeyStoreType(String keyStoreType) {
        System.setProperty("mockserver.javaKeyStoreType", keyStoreType);
    }

    public static boolean saveCertificatesAsPEMFiles() {
        return Boolean.parseBoolean(readPropertyHierarchically("mockserver.saveCertificatesAsPEMFiles", "" + false));
    }

    public static void saveCertificatesAsPEMFiles(boolean saveCertificatesAsPEMFiles) {
        System.setProperty("mockserver.saveCertificatesAsPEMFiles", "" + saveCertificatesAsPEMFiles);
    }

    public static boolean deleteGeneratedKeyStoreOnExit() {
        return Boolean.parseBoolean(readPropertyHierarchically("mockserver.deleteGeneratedKeyStoreOnExit", "" + true));
    }

    public static void deleteGeneratedKeyStoreOnExit(boolean deleteGeneratedKeyStoreOnExit) {
        System.setProperty("mockserver.deleteGeneratedKeyStoreOnExit", "" + deleteGeneratedKeyStoreOnExit);
    }

    public static String sslCertificateDomainName() {
        return readPropertyHierarchically("mockserver.sslCertificateDomainName", SSLFactory.CERTIFICATE_DOMAIN);
    }

    public static void sslCertificateDomainName(String domainName) {
        System.setProperty("mockserver.sslCertificateDomainName", domainName);
    }

    public static String[] sslSubjectAlternativeNameDomains() {
        String sslSubjectAlternativeNameDomains = readPropertyHierarchically("mockserver.sslSubjectAlternativeNameDomains", "");
        if (sslSubjectAlternativeNameDomains.isEmpty()) {
            return new String[0];
        } else {
            return sslSubjectAlternativeNameDomains.split(",");
        }
    }

    public static void sslCertificateDomainName(String[] subjectAlternativeNameDomains) {
        System.setProperty("mockserver.sslSubjectAlternativeNameDomains", Joiner.on(",").join(subjectAlternativeNameDomains));
    }

    public static String[] sslSubjectAlternativeNameIps() {
        String sslSubjectAlternativeNameIps = readPropertyHierarchically("mockserver.sslSubjectAlternativeNameIps", "");
        if (sslSubjectAlternativeNameIps.isEmpty()) {
            return new String[0];
        } else {
            return sslSubjectAlternativeNameIps.split(",");
        }
    }

    public static void sslSubjectAlternativeNameIps(String[] subjectAlternativeNameIps) {
        System.setProperty("mockserver.sslSubjectAlternativeNameIps", Joiner.on(",").join(subjectAlternativeNameIps));
    }

    // mockserver config
    public static int mockServerPort() {
        return readIntegerProperty("mockserver.mockServerHttpPort", -1);
    }

    public static void mockServerPort(int port) {
        System.setProperty("mockserver.mockServerHttpPort", "" + port);
    }

    // proxy config
    public static int proxyPort() {
        return readIntegerProperty("mockserver.proxyHttpPort", -1);
    }

    public static void proxyPort(int port) {
        System.setProperty("mockserver.proxyHttpPort", "" + port);
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
}
