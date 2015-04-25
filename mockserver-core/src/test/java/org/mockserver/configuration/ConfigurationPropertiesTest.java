package org.mockserver.configuration;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Matchers;
import org.mockserver.socket.SSLFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.isNull;

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
        System.clearProperty("mockserver.rebuildKeyStore");
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
        System.clearProperty("mockserver.sslSubjectAlternativeNameDomains");

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameDomains()), empty());
        ConfigurationProperties.sslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameDomains() {
        // given
        System.clearProperty("mockserver.sslSubjectAlternativeNameDomains");

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameDomains()), empty());
        ConfigurationProperties.sslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameIps() {
        // given
        System.clearProperty("mockserver.sslSubjectAlternativeNameIps");

        // when
        assertThat(Arrays.asList(new ConfigurationProperties().sslSubjectAlternativeNameIps()), empty());
        ConfigurationProperties.sslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("1", "2", "3", "4"));
        assertEquals("1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadRebuildKeyStore() {
        // given
        System.clearProperty("mockserver.rebuildKeyStore");

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
        assertEquals(-1, ConfigurationProperties.mockServerPort());
        ConfigurationProperties.mockServerPort(10);

        // then
        assertEquals(10, ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldHandleInvalidServerPort() {
        // given
        System.setProperty("mockserver.mockServerPort", "invalid");

        // then
        assertEquals(-1, ConfigurationProperties.mockServerPort());
    }

    @Test
    public void shouldSetAndReadProxyPort() {
        // given
        System.clearProperty("mockserver.proxyPort");

        // when
        assertEquals(-1, ConfigurationProperties.proxyPort());
        ConfigurationProperties.proxyPort(10);

        // then
        assertEquals(10, ConfigurationProperties.proxyPort());
    }

    @Test
    public void shouldHandleInvalidProxyPort() {
        // given
        System.setProperty("mockserver.proxyPort", "invalid");

        // then
        assertEquals(-1, ConfigurationProperties.proxyPort());
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLogLevel() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("log level \"WRONG\" is not legel it must be one of \"ALL\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\""));

        ConfigurationProperties.overrideLogLevel("WRONG");
    }

    @Test
    public void shouldIgnoreNull() {
        // given
        System.clearProperty("mockserver.logLevel");
        System.clearProperty("root.logLevel");

        // when
        ConfigurationProperties.overrideLogLevel(null);

        // then
        assertNull(System.getProperty("mockserver.logLevel"));
        assertNull(System.getProperty("root.logLevel"));
    }
}
