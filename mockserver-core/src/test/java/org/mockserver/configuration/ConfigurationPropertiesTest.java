package org.mockserver.configuration;

import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class ConfigurationPropertiesTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();
    private String propertiesBeforeTest;

    @Before
    public void backupProperties() throws IOException {
        StringWriter stringWriter = new StringWriter();
        System.getProperties().store(stringWriter, "");
        propertiesBeforeTest = stringWriter.toString();
    }

    @After
    public void restoreProperties() throws IOException {
        java.util.Properties properties = new java.util.Properties();
        properties.load(new StringReader(propertiesBeforeTest));
        System.setProperties(properties);
        ConfigurationProperties.reset();
    }

    @Test
    public void shouldSetAndReadEnableCORSSettingForAPI() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");

        // when
        assertFalse(ConfigurationProperties.enableCORSForAPI());
        ConfigurationProperties.enableCORSForAPI(true);

        // then
        assertTrue(ConfigurationProperties.enableCORSForAPI());
        assertEquals("true", System.getProperty("mockserver.enableCORSForAPI"));
    }

    @Test
    public void shouldDetectEnableCORSSettingForAPIHasBeenExplicitlySet() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");

        // when
        assertFalse(ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());
        ConfigurationProperties.enableCORSForAPI(true);
        assertTrue(ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());
    }

    @Test
    public void shouldSetAndReadEnableCORSSettingForAllResponses() {
        // given
        System.clearProperty("mockserver.enableCORSForAllResponses");

        // when
        assertFalse(ConfigurationProperties.enableCORSForAllResponses());
        ConfigurationProperties.enableCORSForAllResponses(false);

        // then
        assertFalse(ConfigurationProperties.enableCORSForAllResponses());
        assertEquals("false", System.getProperty("mockserver.enableCORSForAllResponses"));
    }

    @Test
    public void shouldSetAndReadNIOEventLoopThreadCount() {
        // given
        System.clearProperty("mockserver.nioEventLoopThreadCount");
        int eventLoopCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 5));

        // when
        assertEquals(eventLoopCount, ConfigurationProperties.nioEventLoopThreadCount());
        ConfigurationProperties.nioEventLoopThreadCount(2);

        // then
        assertEquals(2, ConfigurationProperties.nioEventLoopThreadCount());
    }

    @Test
    public void shouldHandleInvalidNIOEventLoopThreadCount() {
        // given
        System.setProperty("mockserver.nioEventLoopThreadCount", "invalid");
        int eventLoopCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 5));

        // then
        assertEquals(eventLoopCount, ConfigurationProperties.nioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadMaxExpectations() {
        // given
        System.clearProperty("mockserver.maxExpectations");

        // when
        assertEquals(5000, ConfigurationProperties.maxExpectations());
        ConfigurationProperties.maxExpectations(100);

        // then
        assertEquals(100, ConfigurationProperties.maxExpectations());
    }

    @Test
    public void shouldHandleInvalidMaxExpectations() {
        // given
        System.setProperty("mockserver.maxExpectations", "invalid");

        // then
        assertEquals(5000, ConfigurationProperties.maxExpectations());
    }

    @Test
    public void shouldSetAndReadRequestLogSize() {
        // given
        System.clearProperty("mockserver.requestLogSize");

        // when
        assertEquals(5000 * 5000, ConfigurationProperties.requestLogSize());
        ConfigurationProperties.requestLogSize(10);

        // then
        assertEquals(10, ConfigurationProperties.requestLogSize());
    }

    @Test
    public void shouldHandleInvalidRequestLogSize() {
        // given
        System.setProperty("mockserver.requestLogSize", "invalid");

        // then
        assertEquals(5000 * 5000, ConfigurationProperties.requestLogSize());
    }

    @Test
    public void shouldSetAndReadMaxWebSocketExpectations() {
        // given
        System.clearProperty("mockserver.maxWebSocketExpectations");

        // when
        assertEquals(1000, ConfigurationProperties.maxWebSocketExpectations());
        ConfigurationProperties.maxWebSocketExpectations(100);

        // then
        assertEquals(100, ConfigurationProperties.maxWebSocketExpectations());
    }

    @Test
    public void shouldHandleInvalidMaxWebSocketExpectations() {
        // given
        System.setProperty("mockserver.maxWebSocketExpectations", "invalid");

        // then
        assertEquals(1000, ConfigurationProperties.maxWebSocketExpectations());
    }

    @Test
    public void shouldSetAndReadMaxInitialLineLength() {
        // given
        System.clearProperty("mockserver.maxInitialLineLength");

        // when
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxInitialLineLength());
        ConfigurationProperties.maxInitialLineLength(100);

        // then
        assertEquals(100, ConfigurationProperties.maxInitialLineLength());
    }

    @Test
    public void shouldHandleInvalidMaxInitialLineLength() {
        // given
        System.setProperty("mockserver.maxInitialLineLength", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxInitialLineLength());
    }

    @Test
    public void shouldSetAndReadMaxHeaderSize() {
        // given
        System.clearProperty("mockserver.maxHeaderSize");

        // when
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxHeaderSize());
        ConfigurationProperties.maxHeaderSize(100);

        // then
        assertEquals(100, ConfigurationProperties.maxHeaderSize());
    }

    @Test
    public void shouldHandleInvalidMaxHeaderSize() {
        // given
        System.setProperty("mockserver.maxHeaderSize", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxHeaderSize());
    }

    @Test
    public void shouldSetAndReadMaxChunkSize() {
        // given
        System.clearProperty("mockserver.maxChunkSize");

        // when
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxChunkSize());
        ConfigurationProperties.maxChunkSize(100);

        // then
        assertEquals(100, ConfigurationProperties.maxChunkSize());
    }

    @Test
    public void shouldHandleInvalidMaxChunkSize() {
        // given
        System.setProperty("mockserver.maxChunkSize", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, ConfigurationProperties.maxChunkSize());
    }

    @Test
    public void shouldSetAndReadMaxSocketTimeout() {
        // given
        System.clearProperty("mockserver.maxSocketTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(20), ConfigurationProperties.maxSocketTimeout());
        ConfigurationProperties.maxSocketTimeout(100);

        // then
        assertEquals(100, ConfigurationProperties.maxSocketTimeout());
    }

    @Test
    public void shouldHandleInvalidMaxSocketTimeout() {
        // given
        System.setProperty("mockserver.maxSocketTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(20), ConfigurationProperties.maxSocketTimeout());
    }

    @Test
    public void shouldSetAndReadSocketConnectionTimeout() {
        // given
        System.clearProperty("mockserver.socketConnectionTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(20), ConfigurationProperties.socketConnectionTimeout());
        ConfigurationProperties.socketConnectionTimeout(100);

        // then
        assertEquals(100, ConfigurationProperties.socketConnectionTimeout());
    }

    @Test
    public void shouldHandleInvalidSocketConnectionTimeout() {
        // given
        System.setProperty("mockserver.socketConnectionTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(20), ConfigurationProperties.socketConnectionTimeout());
    }

    @Test
    public void shouldSetAndReadJavaKeyStoreFilePath() {
        // given
        System.clearProperty("mockserver.javaKeyStoreFilePath");

        // when
        assertEquals(KeyStoreFactory.defaultKeyStoreFileName(), ConfigurationProperties.javaKeyStoreFilePath());
        ConfigurationProperties.javaKeyStoreFilePath("newKeyStoreFile.jks");

        // then
        assertEquals("newKeyStoreFile.jks", ConfigurationProperties.javaKeyStoreFilePath());
        assertEquals("newKeyStoreFile.jks", System.getProperty("mockserver.javaKeyStoreFilePath"));
        assertTrue(ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadJavaKeyStorePassword() {
        // given
        System.clearProperty("mockserver.javaKeyStorePassword");

        // when
        assertEquals(KeyStoreFactory.KEY_STORE_PASSWORD, ConfigurationProperties.javaKeyStorePassword());
        ConfigurationProperties.javaKeyStorePassword("newPassword");

        // then
        assertEquals("newPassword", ConfigurationProperties.javaKeyStorePassword());
        assertEquals("newPassword", System.getProperty("mockserver.javaKeyStorePassword"));
        assertTrue(ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadJavaKeyStoreType() {
        // given
        System.clearProperty("mockserver.javaKeyStoreType");

        // when
        assertEquals(KeyStore.getDefaultType(), ConfigurationProperties.javaKeyStoreType());
        ConfigurationProperties.javaKeyStoreType("PKCS11");

        // then
        assertEquals("PKCS11", ConfigurationProperties.javaKeyStoreType());
        assertEquals("PKCS11", System.getProperty("mockserver.javaKeyStoreType"));
        assertTrue(ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadDeleteGeneratedKeyStoreOnExit() {
        // given
        System.clearProperty("mockserver.deleteGeneratedKeyStoreOnExit");

        // when
        assertTrue(ConfigurationProperties.deleteGeneratedKeyStoreOnExit());
        ConfigurationProperties.deleteGeneratedKeyStoreOnExit(false);

        // then
        assertFalse(ConfigurationProperties.deleteGeneratedKeyStoreOnExit());
        assertEquals("false", System.getProperty("mockserver.deleteGeneratedKeyStoreOnExit"));
    }

    @Test
    public void shouldSetAndReadSslCertificateDomainName() {
        // given
        System.clearProperty("mockserver.sslCertificateDomainName");

        // when
        assertEquals(KeyStoreFactory.CERTIFICATE_DOMAIN, ConfigurationProperties.sslCertificateDomainName());
        ConfigurationProperties.sslCertificateDomainName("newDomain");

        // then
        assertEquals("newDomain", ConfigurationProperties.sslCertificateDomainName());
        assertEquals("newDomain", System.getProperty("mockserver.sslCertificateDomainName"));
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();

        // when
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), empty());
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        ConfigurationProperties.rebuildServerKeyStore(false);

        // when
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), empty());
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());

        // given
        ConfigurationProperties.rebuildServerKeyStore(false);

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertFalse(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameIps() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameIps();

        // when
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        ConfigurationProperties.addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameIps() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameIps();
        ConfigurationProperties.rebuildServerKeyStore(false);

        // when
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        ConfigurationProperties.addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());

        // given
        ConfigurationProperties.rebuildServerKeyStore(false);

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertFalse(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldSetAndReadRebuildKeyStore() {
        // given
        ConfigurationProperties.rebuildServerKeyStore(false);

        // when
        assertFalse(ConfigurationProperties.rebuildKeyStore());
        ConfigurationProperties.rebuildServerKeyStore(true);

        // then
        assertTrue(ConfigurationProperties.rebuildServerKeyStore());
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLogLevel() {
        try {
            ConfigurationProperties.logLevel("WRONG");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("log level \"WRONG\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\" or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\""));
        }
    }

    @Test
    public void shouldSetAndReadCertificateAuthorityCertificate() {
        // given
        System.clearProperty("mockserver.certificateAuthorityCertificate");

        // when
        assertEquals("org/mockserver/socket/CertificateAuthorityCertificate.pem", ConfigurationProperties.certificateAuthorityCertificate());
        ConfigurationProperties.certificateAuthorityCertificate("some/certificate.pem");

        // then
        assertEquals("some/certificate.pem", ConfigurationProperties.certificateAuthorityCertificate());
        assertEquals("some/certificate.pem", System.getProperty("mockserver.certificateAuthorityCertificate"));
    }

    @Test
    public void shouldSetAndReadPreventCertificateDynamicUpdate() {
        // given
        System.clearProperty("mockserver.preventCertificateDynamicUpdate");

        // when
        assertFalse(ConfigurationProperties.preventCertificateDynamicUpdate());
        ConfigurationProperties.preventCertificateDynamicUpdate(false);

        // then
        assertFalse(ConfigurationProperties.preventCertificateDynamicUpdate());
        assertEquals("false", System.getProperty("mockserver.preventCertificateDynamicUpdate"));
    }

    @Test
    public void shouldSetAndReadCertificateAuthorityPrivateKey() {
        // given
        System.clearProperty("mockserver.certificateAuthorityPrivateKey");

        // when
        assertEquals("org/mockserver/socket/CertificateAuthorityPrivateKey.pem", ConfigurationProperties.certificateAuthorityPrivateKey());
        ConfigurationProperties.certificateAuthorityPrivateKey("some/private_key.pem");

        // then
        assertEquals("some/private_key.pem", ConfigurationProperties.certificateAuthorityPrivateKey());
        assertEquals("some/private_key.pem", System.getProperty("mockserver.certificateAuthorityPrivateKey"));
    }

    @Test
    public void shouldSetAndReadLogLevelUsingSLF4J() {
        // SAVE
        String previousValue = ConfigurationProperties.logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");

            // when
            assertEquals(Level.INFO, ConfigurationProperties.logLevel());
            ConfigurationProperties.logLevel("TRACE");

            // then
            assertEquals(Level.TRACE, ConfigurationProperties.logLevel());
            assertEquals("FINEST", ConfigurationProperties.javaLoggerLogLevel());
            assertEquals("TRACE", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            ConfigurationProperties.logLevel(previousValue);
        }
    }

    @Test
    public void shouldSetAndReadLogLevelUsingJavaLogger() {
        // SAVE
        String previousValue = ConfigurationProperties.logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");
            ConfigurationProperties.logLevel(null);

            // when
            assertEquals(Level.INFO, ConfigurationProperties.logLevel());
            ConfigurationProperties.logLevel("FINEST");

            // then
            assertEquals(Level.TRACE, ConfigurationProperties.logLevel());
            assertEquals("FINEST", ConfigurationProperties.javaLoggerLogLevel());
            assertEquals("FINEST", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            ConfigurationProperties.logLevel(previousValue);
        }
    }

    @Test
    public void shouldSetAndReadMetricsEnabled() {
        // given
        System.clearProperty("mockserver.metricsEnabled");

        // when
        assertFalse(ConfigurationProperties.metricsEnabled());
        ConfigurationProperties.metricsEnabled(true);

        // then
        assertTrue(ConfigurationProperties.metricsEnabled());
        assertEquals("true", System.getProperty("mockserver.metricsEnabled"));
    }

    @Test
    public void shouldSetAndReadDisableSystemOut() {
        // when
        ConfigurationProperties.disableSystemOut(true);

        // then
        assertTrue(ConfigurationProperties.disableSystemOut());
        assertEquals("true", System.getProperty("mockserver.disableSystemOut"));

        // when
        ConfigurationProperties.disableSystemOut(false);

        // then
        assertFalse(ConfigurationProperties.disableSystemOut());
        assertEquals("false", System.getProperty("mockserver.disableSystemOut"));
    }

    @Test
    public void shouldSetAndReadHttpProxy() {
        // given
        System.clearProperty("mockserver.httpProxy");

        // when
        assertNull(ConfigurationProperties.httpProxy());
        ConfigurationProperties.httpProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", ConfigurationProperties.httpProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.httpProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidHttpProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid httpProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        ConfigurationProperties.httpProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadHttpsProxy() {
        // given
        System.clearProperty("mockserver.httpsProxy");

        // when
        assertNull(ConfigurationProperties.httpsProxy());
        ConfigurationProperties.httpsProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", ConfigurationProperties.httpsProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.httpsProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidHttpsProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid httpsProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        ConfigurationProperties.httpsProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadSocksProxy() {
        // given
        System.clearProperty("mockserver.socksProxy");

        // when
        assertNull(ConfigurationProperties.socksProxy());
        ConfigurationProperties.socksProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", ConfigurationProperties.socksProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.socksProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidSocksProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid socksProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        ConfigurationProperties.socksProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadLocalBoundIP() {
        // given
        System.clearProperty("mockserver.localBoundIP");

        // when
        assertEquals("", ConfigurationProperties.localBoundIP());
        ConfigurationProperties.localBoundIP("127.0.0.1");

        // then
        assertEquals("127.0.0.1", ConfigurationProperties.localBoundIP());
        assertEquals("127.0.0.1", System.getProperty("mockserver.localBoundIP"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLocalBoundIP() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("'abc.def' is not an IP string literal"));

        ConfigurationProperties.localBoundIP("abc.def");
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationRealm() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationRealm");

        // when
        assertEquals("MockServer HTTP Proxy", ConfigurationProperties.proxyAuthenticationRealm());
        ConfigurationProperties.proxyAuthenticationRealm("my realm");

        // then
        assertEquals("my realm", ConfigurationProperties.proxyAuthenticationRealm());
        assertEquals("my realm", System.getProperty("mockserver.proxyAuthenticationRealm"));
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationUsername() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationUsername");

        // when
        assertEquals("", ConfigurationProperties.proxyAuthenticationUsername());
        ConfigurationProperties.proxyAuthenticationUsername("john.doe");

        // then
        assertEquals("john.doe", ConfigurationProperties.proxyAuthenticationUsername());
        assertEquals("john.doe", System.getProperty("mockserver.proxyAuthenticationUsername"));
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationPassword() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationPassword");

        // when
        assertEquals("", ConfigurationProperties.proxyAuthenticationPassword());
        ConfigurationProperties.proxyAuthenticationPassword("p@ssw0rd");

        // then
        assertEquals("p@ssw0rd", ConfigurationProperties.proxyAuthenticationPassword());
        assertEquals("p@ssw0rd", System.getProperty("mockserver.proxyAuthenticationPassword"));
    }
}
