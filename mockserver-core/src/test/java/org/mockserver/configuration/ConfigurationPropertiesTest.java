package org.mockserver.configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.socket.SSLFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class ConfigurationPropertiesTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();
    String propertiesBeforeTest;

    @Before
    public void backupProperties() throws IOException {
        StringWriter stringWriter = new StringWriter();
        System.getProperties().store(stringWriter, "");
        propertiesBeforeTest = stringWriter.toString();
        ConfigurationProperties.rebuildKeyStore(false);
    }

    @After
    public void restoreProperties() throws IOException {
        java.util.Properties properties = new java.util.Properties();
        properties.load(new StringReader(propertiesBeforeTest));
        System.setProperties(properties);
    }

    @Test
    public void shouldSetAndReadMaxSocketTimeout() {
        // given
        System.clearProperty("mockserver.maxTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(ConfigurationProperties.DEFAULT_MAX_TIMEOUT), new ConfigurationProperties().maxSocketTimeout());
        ConfigurationProperties.maxSocketTimeout(100);

        // then
        assertEquals(100, ConfigurationProperties.maxSocketTimeout());
    }

    @Test
    public void shouldHandleInvalidMaxSocketTimeout() {
        // given
        System.setProperty("mockserver.maxTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(ConfigurationProperties.DEFAULT_MAX_TIMEOUT), ConfigurationProperties.maxSocketTimeout());
    }

    @Test
    public void shouldSetAndReadJavaKeyStoreFilePath() {
        // given
        System.clearProperty("mockserver.javaKeyStoreFilePath");

        // when
        assertEquals(SSLFactory.defaultKeyStoreFileName(), new ConfigurationProperties().javaKeyStoreFilePath());
        ConfigurationProperties.javaKeyStoreFilePath("newKeyStoreFile.jks");

        // then
        assertEquals("newKeyStoreFile.jks", ConfigurationProperties.javaKeyStoreFilePath());
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadJavaKeyStorePassword() {
        // given
        System.clearProperty("mockserver.javaKeyStorePassword");

        // when
        assertEquals(SSLFactory.KEY_STORE_PASSWORD, new ConfigurationProperties().javaKeyStorePassword());
        ConfigurationProperties.javaKeyStorePassword("newPassword");

        // then
        assertEquals("newPassword", ConfigurationProperties.javaKeyStorePassword());
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadJavaKeyStoreType() {
        // given
        System.clearProperty("mockserver.javaKeyStoreType");

        // when
        assertEquals(KeyStore.getDefaultType(), new ConfigurationProperties().javaKeyStoreType());
        ConfigurationProperties.javaKeyStoreType("PKCS11");

        // then
        assertEquals("PKCS11", ConfigurationProperties.javaKeyStoreType());
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadDeleteGeneratedKeyStoreOnExit() {
        // given
        System.clearProperty("mockserver.deleteGeneratedKeyStoreOnExit");

        // when
        assertEquals(true, new ConfigurationProperties().deleteGeneratedKeyStoreOnExit());
        ConfigurationProperties.deleteGeneratedKeyStoreOnExit(false);

        // then
        assertEquals(false, ConfigurationProperties.deleteGeneratedKeyStoreOnExit());
    }

    @Test
    public void shouldSetAndReadSslCertificateDomainName() {
        // given
        System.clearProperty("mockserver.sslCertificateDomainName");

        // when
        assertEquals(SSLFactory.CERTIFICATE_DOMAIN, new ConfigurationProperties().sslCertificateDomainName());
        ConfigurationProperties.sslCertificateDomainName("newDomain");

        // then
        assertEquals("newDomain", ConfigurationProperties.sslCertificateDomainName());
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameDomains()), containsInAnyOrder("localhost"));
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "localhost"));
        assertEquals("a,b,c,d,localhost", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameDomains()), containsInAnyOrder("localhost"));
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "localhost"));
        assertEquals("a,b,c,d,localhost", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g", "localhost"));
        assertEquals("a,b,c,d,e,f,g,localhost", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());

        // given
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g", "localhost"));
        assertEquals("a,b,c,d,e,f,g,localhost", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(false, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameIps() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameIps();

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        ConfigurationProperties.addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameIps() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameIps();
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        ConfigurationProperties.addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());

        // given
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertEquals(false, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadRebuildKeyStore() {
        // given
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        assertEquals(false, new ConfigurationProperties().rebuildKeyStore());
        ConfigurationProperties.rebuildKeyStore(true);

        // then
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadServerPort() {
        // given
        System.clearProperty("mockserver.mockServerPort");

        // when
        assertEquals(Arrays.asList(-1), ConfigurationProperties.mockServerPort());
        ConfigurationProperties.mockServerPort(10);

        // then
        assertEquals("10", System.getProperty("mockserver.mockServerPort"));
        assertEquals(Arrays.asList(10), ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldSetAndReadServerPortAsList() {
        // given
        System.clearProperty("mockserver.mockServerPort");

        // when
        assertEquals(Arrays.asList(-1), ConfigurationProperties.mockServerPort());
        ConfigurationProperties.mockServerPort(10, 20, 30);

        // then
        assertEquals("10,20,30", System.getProperty("mockserver.mockServerPort"));
        assertEquals(Arrays.asList(10, 20, 30), ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldHandleInvalidServerPort() {
        // given
        System.setProperty("mockserver.mockServerPort", "invalid");

        // then
        assertEquals(Arrays.asList(), ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldSetAndReadProxyPort() {
        // given
        System.clearProperty("mockserver.proxyPort");

        // when
        assertEquals(new Integer(-1), ConfigurationProperties.proxyPort());
        ConfigurationProperties.proxyPort(10);

        // then
        assertEquals("10", System.getProperty("mockserver.proxyPort"));
        assertEquals(new Integer(10), ConfigurationProperties.proxyPort());
    }

    @Test
    public void shouldHandleInvalidProxyPort() {
        // given
        System.setProperty("mockserver.proxyPort", "invalid");

        // then
        assertEquals(new Integer(-1), ConfigurationProperties.proxyPort());
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLogLevel() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("log level \"WRONG\" is not legal it must be one of \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\""));

        ConfigurationProperties.overrideLogLevel("WRONG");
    }

    @Test
    public void shouldIgnoreNull() {
        // given
        System.clearProperty("mockserver.logLevel");

        // when
        ConfigurationProperties.overrideLogLevel(null);

        // then
        assertNull(System.getProperty("mockserver.logLevel"));
    }

    @Test
    public void shouldSetLogLevel() {
        // given
        System.clearProperty("mockserver.logLevel");

        // when
        ConfigurationProperties.overrideLogLevel("TRACE");

        // then
        assertThat(System.getProperty("mockserver.logLevel"), is("TRACE"));
    }
}
