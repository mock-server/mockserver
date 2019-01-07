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
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
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
    public void shouldSetAndReadEnableCORSSettingForAPI() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");

        // when
        assertEquals(true, ConfigurationProperties.enableCORSForAPI());
        ConfigurationProperties.enableCORSForAPI(false);

        // then
        assertEquals(false, ConfigurationProperties.enableCORSForAPI());
        assertEquals("false", System.getProperty("mockserver.enableCORSForAPI"));
    }

    @Test
    public void shouldDetectEnableCORSSettingForAPIHasBeenExplicitlySet() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");

        // when
        assertEquals(false, ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());
        ConfigurationProperties.enableCORSForAPI(true);
        assertEquals(true, ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());

        // given
        System.clearProperty("mockserver.enableCORSForAPI");

        // when
        assertEquals(false, ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());
        System.setProperty("mockserver.enableCORSForAPI", "" + true);
        assertEquals(true, ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly());
    }

    @Test
    public void shouldSetAndReadEnableCORSSettingForAllResponses() {
        // given
        System.clearProperty("mockserver.enableCORSForAllResponses");

        // when
        assertEquals(false, ConfigurationProperties.enableCORSForAllResponses());
        ConfigurationProperties.enableCORSForAllResponses(false);

        // then
        assertEquals(false, ConfigurationProperties.enableCORSForAllResponses());
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
        assertEquals(1000, ConfigurationProperties.maxExpectations());
        ConfigurationProperties.maxExpectations(100);

        // then
        assertEquals(100, ConfigurationProperties.maxExpectations());
    }

    @Test
    public void shouldHandleInvalidMaxExpectations() {
        // given
        System.setProperty("mockserver.maxExpectations", "invalid");

        // then
        assertEquals(1000, ConfigurationProperties.maxExpectations());
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
        assertEquals(4096, ConfigurationProperties.maxInitialLineLength());
        ConfigurationProperties.maxInitialLineLength(100);

        // then
        assertEquals(100, ConfigurationProperties.maxInitialLineLength());
    }

    @Test
    public void shouldHandleInvalidMaxInitialLineLength() {
        // given
        System.setProperty("mockserver.maxInitialLineLength", "invalid");

        // then
        assertEquals(4096, ConfigurationProperties.maxInitialLineLength());
    }

    @Test
    public void shouldSetAndReadMaxHeaderSize() {
        // given
        System.clearProperty("mockserver.maxHeaderSize");

        // when
        assertEquals(8192, ConfigurationProperties.maxHeaderSize());
        ConfigurationProperties.maxHeaderSize(100);

        // then
        assertEquals(100, ConfigurationProperties.maxHeaderSize());
    }

    @Test
    public void shouldHandleInvalidMaxHeaderSize() {
        // given
        System.setProperty("mockserver.maxHeaderSize", "invalid");

        // then
        assertEquals(8192, ConfigurationProperties.maxHeaderSize());
    }

    @Test
    public void shouldSetAndReadMaxChunkSize() {
        // given
        System.clearProperty("mockserver.maxChunkSize");

        // when
        assertEquals(8192, ConfigurationProperties.maxChunkSize());
        ConfigurationProperties.maxChunkSize(100);

        // then
        assertEquals(100, ConfigurationProperties.maxChunkSize());
    }

    @Test
    public void shouldHandleInvalidMaxChunkSize() {
        // given
        System.setProperty("mockserver.maxChunkSize", "invalid");

        // then
        assertEquals(8192, ConfigurationProperties.maxChunkSize());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldSetAndReadDeleteGeneratedKeyStoreOnExit() {
        // given
        System.clearProperty("mockserver.deleteGeneratedKeyStoreOnExit");

        // when
        assertEquals(true, ConfigurationProperties.deleteGeneratedKeyStoreOnExit());
        ConfigurationProperties.deleteGeneratedKeyStoreOnExit(false);

        // then
        assertEquals(false, ConfigurationProperties.deleteGeneratedKeyStoreOnExit());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        ConfigurationProperties.rebuildKeyStore(false);

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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());

        // given
        ConfigurationProperties.rebuildKeyStore(false);

        // when
        ConfigurationProperties.addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(ConfigurationProperties.sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertEquals(false, ConfigurationProperties.rebuildKeyStore());
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
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameIps() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameIps();
        ConfigurationProperties.rebuildKeyStore(false);

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
        assertEquals(false, ConfigurationProperties.rebuildKeyStore());
        ConfigurationProperties.rebuildKeyStore(true);

        // then
        assertEquals(true, ConfigurationProperties.rebuildKeyStore());
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLogLevel() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("log level \"WRONG\" is not legal it must be one of \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\""));

        ConfigurationProperties.logLevel("WRONG");
    }

    @Test
    public void shouldSetAndReadLogLevel() {
        // given
        System.clearProperty("mockserver.logLevel");

        // when
        assertEquals(Level.INFO, ConfigurationProperties.logLevel());
        ConfigurationProperties.logLevel("TRACE");

        // then
        assertEquals(Level.TRACE, ConfigurationProperties.logLevel());
        assertEquals("TRACE", System.getProperty("mockserver.logLevel"));
    }

    @Test
    public void shouldSetAndReadDisableRequestAudit() {
        // given
        System.clearProperty("mockserver.disableRequestAudit");

        // when
        assertFalse(ConfigurationProperties.disableRequestAudit());
        ConfigurationProperties.disableRequestAudit(false);

        // then
        assertFalse(ConfigurationProperties.disableRequestAudit());
        assertEquals("false", System.getProperty("mockserver.disableRequestAudit"));
    }

    @Test
    public void shouldSetAndReadDisableSystemOut() {
        // given
        System.clearProperty("mockserver.disableSystemOut");

        // when
        assertFalse(ConfigurationProperties.disableSystemOut());
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
