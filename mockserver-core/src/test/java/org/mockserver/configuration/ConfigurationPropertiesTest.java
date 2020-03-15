package org.mockserver.configuration;

import io.netty.util.NettyRuntime;
import io.netty.util.internal.SystemPropertyUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.server.initialize.ExpectationInitializerExample;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockserver.configuration.ConfigurationProperties.*;

/**
 * @author jamesdbloom
 */
public class ConfigurationPropertiesTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
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
    public void shouldSetAndReadNIOEventLoopThreadCount() {
        // given
        System.clearProperty("mockserver.nioEventLoopThreadCount");
        int eventLoopCount = Math.max(35, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors()));

        // when
        assertEquals(eventLoopCount, nioEventLoopThreadCount());
        nioEventLoopThreadCount(2);

        // then
        assertEquals("2", System.getProperty("mockserver.nioEventLoopThreadCount"));
        assertEquals(2, nioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadActionHandlerThreadCount() {
        // given
        System.clearProperty("mockserver.actionHandlerThreadCount");
        int actionHandlerThreadCount = Math.max(20, Runtime.getRuntime().availableProcessors());

        // when
        assertEquals(actionHandlerThreadCount, actionHandlerThreadCount());
        actionHandlerThreadCount(2);

        // then
        assertEquals("2", System.getProperty("mockserver.actionHandlerThreadCount"));
        assertEquals(2, actionHandlerThreadCount());
    }

    @Test
    public void shouldSetAndReadWebSocketClientEventLoopThreadCount() {
        // given
        System.clearProperty("mockserver.webSocketClientEventLoopThreadCount");

        // when
        assertEquals(5, webSocketClientEventLoopThreadCount());
        webSocketClientEventLoopThreadCount(2);

        // then
        assertEquals("2", System.getProperty("mockserver.webSocketClientEventLoopThreadCount"));
        assertEquals(2, webSocketClientEventLoopThreadCount());
    }

    @Test
    public void shouldHandleInvalidNIOEventLoopThreadCount() {
        // given
        System.setProperty("mockserver.nioEventLoopThreadCount", "invalid");
        int eventLoopCount = Math.max(35, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors()));

        // then
        assertEquals(eventLoopCount, nioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadMaxExpectations() {
        // given
        System.clearProperty("mockserver.maxExpectations");

        // when
        assertEquals(5000, maxExpectations());
        maxExpectations(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxExpectations"));
        assertEquals(100, maxExpectations());
    }

    @Test
    public void shouldHandleInvalidMaxExpectations() {
        // given
        System.setProperty("mockserver.maxExpectations", "invalid");

        // then
        assertEquals(5000, maxExpectations());
    }

    @Test
    public void shouldSetAndReadRequestLogSize() {
        // given
        System.clearProperty("mockserver.maxLogEntries");

        // when
        assertEquals(maxExpectations() * maxExpectations(), maxLogEntries());
        maxLogEntries(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxLogEntries"));
        assertEquals(100, maxLogEntries());
    }

    @Test
    public void shouldHandleInvalidRequestLogSize() {
        // given
        System.setProperty("mockserver.requestLogSize", "invalid");

        // then
        assertEquals(5000 * 5000, maxLogEntries());
    }

    @Test
    public void shouldSetAndReadMaxWebSocketExpectations() {
        // given
        System.clearProperty("mockserver.maxWebSocketExpectations");

        // when
        assertEquals(1500, maxWebSocketExpectations());
        maxWebSocketExpectations(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxWebSocketExpectations"));
        assertEquals(100, maxWebSocketExpectations());
    }

    @Test
    public void shouldHandleInvalidMaxWebSocketExpectations() {
        // given
        System.setProperty("mockserver.maxWebSocketExpectations", "invalid");

        // then
        assertEquals(1500, maxWebSocketExpectations());
    }

    @Test
    public void shouldSetAndReadMaxInitialLineLength() {
        // given
        System.clearProperty("mockserver.maxInitialLineLength");

        // when
        assertEquals(Integer.MAX_VALUE, maxInitialLineLength());
        maxInitialLineLength(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxInitialLineLength"));
        assertEquals(100, maxInitialLineLength());
    }

    @Test
    public void shouldHandleInvalidMaxInitialLineLength() {
        // given
        System.setProperty("mockserver.maxInitialLineLength", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, maxInitialLineLength());
    }

    @Test
    public void shouldSetAndReadMaxHeaderSize() {
        // given
        System.clearProperty("mockserver.maxHeaderSize");

        // when
        assertEquals(Integer.MAX_VALUE, maxHeaderSize());
        maxHeaderSize(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxHeaderSize"));
        assertEquals(100, maxHeaderSize());
    }

    @Test
    public void shouldHandleInvalidMaxHeaderSize() {
        // given
        System.setProperty("mockserver.maxHeaderSize", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, maxHeaderSize());
    }

    @Test
    public void shouldSetAndReadMaxChunkSize() {
        // given
        System.clearProperty("mockserver.maxChunkSize");

        // when
        assertEquals(Integer.MAX_VALUE, maxChunkSize());
        maxChunkSize(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxChunkSize"));
        assertEquals(100, maxChunkSize());
    }

    @Test
    public void shouldHandleInvalidMaxChunkSize() {
        // given
        System.setProperty("mockserver.maxChunkSize", "invalid");

        // then
        assertEquals(Integer.MAX_VALUE, maxChunkSize());
    }

    @Test
    public void shouldSetAndReadMaxSocketTimeout() {
        // given
        System.clearProperty("mockserver.maxSocketTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(20), maxSocketTimeout());
        maxSocketTimeout(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxSocketTimeout"));
        assertEquals(100, maxSocketTimeout());
    }

    @Test
    public void shouldSetAndReadMaxFutureTimeout() {
        // given
        System.clearProperty("mockserver.maxFutureTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(60), maxFutureTimeout());
        maxFutureTimeout(100);

        // then
        assertEquals("100", System.getProperty("mockserver.maxFutureTimeout"));
        assertEquals(100, maxFutureTimeout());
    }

    @Test
    public void shouldHandleInvalidMaxSocketTimeout() {
        // given
        System.setProperty("mockserver.maxSocketTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(20), maxSocketTimeout());
    }

    @Test
    public void shouldSetAndReadSocketConnectionTimeout() {
        // given
        System.clearProperty("mockserver.socketConnectionTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(20), socketConnectionTimeout());
        socketConnectionTimeout(100);

        // then
        assertEquals("100", System.getProperty("mockserver.socketConnectionTimeout"));
        assertEquals(100, socketConnectionTimeout());
    }

    @Test
    public void shouldSetAndReadAlwaysCloseSocketConnections() {
        // given
        System.clearProperty("mockserver.alwaysCloseSocketConnections");

        // when
        assertFalse(alwaysCloseSocketConnections());
        alwaysCloseSocketConnections(true);

        // then
        assertTrue(alwaysCloseSocketConnections());
        assertEquals("true", System.getProperty("mockserver.alwaysCloseSocketConnections"));
    }

    @Test
    public void shouldHandleInvalidSocketConnectionTimeout() {
        // given
        System.setProperty("mockserver.socketConnectionTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(20), socketConnectionTimeout());
    }

    @Test
    public void shouldSetAndReadSslCertificateDomainName() {
        // given
        System.clearProperty("mockserver.sslCertificateDomainName");

        // when
        assertEquals(CertificateSigningRequest.CERTIFICATE_DOMAIN, sslCertificateDomainName());
        sslCertificateDomainName("newDomain");

        // then
        assertEquals("newDomain", sslCertificateDomainName());
        assertEquals("newDomain", System.getProperty("mockserver.sslCertificateDomainName"));
        assertTrue(rebuildServerTLSContext());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();

        // when
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), empty());
        addSslSubjectAlternativeNameDomains("a", "b", "c", "d");

        // then
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertTrue(rebuildServerTLSContext());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameDomains() {
        // given
        ConfigurationProperties.clearSslSubjectAlternativeNameDomains();
        rebuildServerTLSContext(false);

        // when
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), empty());
        addSslSubjectAlternativeNameDomains("a", "b", "c");
        addSubjectAlternativeName("d:1080");

        // then
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d"));
        assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));

        // when
        addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertTrue(rebuildServerTLSContext());

        // given
        rebuildServerTLSContext(false);

        // when
        addSslSubjectAlternativeNameDomains("e", "f", "g");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(sslSubjectAlternativeNameDomains()), containsInAnyOrder("a", "b", "c", "d", "e", "f", "g"));
        assertEquals("a,b,c,d,e,f,g", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        assertFalse(rebuildServerTLSContext());
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameIps() {
        // given
        clearSslSubjectAlternativeNameIps();

        // when
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertTrue(rebuildServerTLSContext());
    }

    @Test
    public void shouldAddSslSubjectAlternativeNameIps() {
        // given
        clearSslSubjectAlternativeNameIps();
        rebuildServerTLSContext(false);

        // when
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("127.0.0.1", "0.0.0.0"));
        addSslSubjectAlternativeNameIps("1", "2", "3", "4");

        // then
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));

        // when
        addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - add subject alternative domain names
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertTrue(rebuildServerTLSContext());

        // given
        rebuildServerTLSContext(false);

        // when
        addSslSubjectAlternativeNameIps("5", "6", "7");

        // then - do not add duplicate subject alternative domain names
        assertThat(Arrays.asList(sslSubjectAlternativeNameIps()), containsInAnyOrder("0.0.0.0", "1", "127.0.0.1", "2", "3", "4", "5", "6", "7"));
        assertEquals("0.0.0.0,1,127.0.0.1,2,3,4,5,6,7", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        assertFalse(rebuildServerTLSContext());
    }

    @Test
    public void shouldSetAndReadRebuildKeyStore() {
        // given
        rebuildServerTLSContext(false);

        // when
        assertFalse(rebuildTLSContext());
        rebuildServerTLSContext(true);

        // then
        assertTrue(rebuildServerTLSContext());
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLogLevel() {
        try {
            logLevel("WRONG");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("log level \"WRONG\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\", or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\""));
        }
    }

    @Test
    public void shouldSetAndReadPreventCertificateDynamicUpdate() {
        // given
        System.clearProperty("mockserver.preventCertificateDynamicUpdate");

        // when
        assertFalse(preventCertificateDynamicUpdate());
        preventCertificateDynamicUpdate(false);

        // then
        assertFalse(preventCertificateDynamicUpdate());
        assertEquals("false", System.getProperty("mockserver.preventCertificateDynamicUpdate"));
    }

    @Test
    public void shouldSetAndReadCertificateAuthorityPrivateKey() {
        // given
        System.clearProperty("mockserver.certificateAuthorityPrivateKey");

        // when
        assertEquals("org/mockserver/socket/PKCS8CertificateAuthorityPrivateKey.pem", certificateAuthorityPrivateKey());
        certificateAuthorityPrivateKey("some/private_key.pem");

        // then
        assertEquals("some/private_key.pem", certificateAuthorityPrivateKey());
        assertEquals("some/private_key.pem", System.getProperty("mockserver.certificateAuthorityPrivateKey"));
    }

    @Test
    public void shouldSetAndReadCertificateAuthorityCertificate() throws IOException {
        // given
        System.clearProperty("mockserver.certificateAuthorityCertificate");

        // when
        assertEquals("org/mockserver/socket/CertificateAuthorityCertificate.pem", certificateAuthorityCertificate());
        File tempFile = File.createTempFile("prefix", "suffix");
        certificateAuthorityCertificate(tempFile.getAbsolutePath());

        // then
        assertEquals(tempFile.getAbsolutePath(), certificateAuthorityCertificate());
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.certificateAuthorityCertificate"));
    }

    @Test
    public void shouldSetAndReadDynamicallyCreateCertificateAuthorityCertificate() {
        // given
        System.clearProperty("mockserver.dynamicallyCreateCertificateAuthorityCertificate");

        // when
        assertFalse(dynamicallyCreateCertificateAuthorityCertificate());
        dynamicallyCreateCertificateAuthorityCertificate(true);

        // then
        assertTrue(dynamicallyCreateCertificateAuthorityCertificate());
        assertEquals("true", System.getProperty("mockserver.dynamicallyCreateCertificateAuthorityCertificate"));
    }

    @Test
    public void shouldSetAndReadDirectoryToSaveDynamicSSLCertificate() throws IOException {
        // given
        System.clearProperty("mockserver.directoryToSaveDynamicSSLCertificate");

        // when
        assertThat(directoryToSaveDynamicSSLCertificate(), is(""));
        try {
            directoryToSaveDynamicSSLCertificate("some/random/path");
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            assertThat(throwable, instanceOf(RuntimeException.class));
            assertThat(throwable.getMessage(), is("some/random/path does not exist or is not accessible"));
        }

        // when
        File tempFile = File.createTempFile("prefix", "suffix");
        directoryToSaveDynamicSSLCertificate(tempFile.getAbsolutePath());

        // then
        assertThat(directoryToSaveDynamicSSLCertificate(), is(tempFile.getAbsolutePath()));
        assertThat(System.getProperty("mockserver.directoryToSaveDynamicSSLCertificate"), is(tempFile.getAbsolutePath()));
    }

    @Test
    public void shouldSetAndReadPrivateKeyPath() throws IOException {
        // given
        System.clearProperty("mockserver.privateKeyPath");

        // then
        assertThat(privateKeyPath(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        privateKeyPath(tempFile.getAbsolutePath());

        // then
        assertThat(privateKeyPath(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.privateKeyPath"));
    }

    @Test
    public void shouldSetAndReadX509CertificatePath() throws IOException {
        // given
        System.clearProperty("mockserver.x509CertificatePath");

        // then
        assertThat(x509CertificatePath(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        x509CertificatePath(tempFile.getAbsolutePath());

        // then
        assertThat(x509CertificatePath(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.x509CertificatePath"));
    }

    @Test
    public void shouldSetAndReadTLSMutualAuthenticationRequired() {
        // given
        System.clearProperty("mockserver.tlsMutualAuthenticationRequired");

        // then
        assertFalse(tlsMutualAuthenticationRequired());

        // when
        tlsMutualAuthenticationRequired(true);

        // then
        assertTrue(tlsMutualAuthenticationRequired());
        assertEquals("true", System.getProperty("mockserver.tlsMutualAuthenticationRequired"));

        // when
        tlsMutualAuthenticationRequired(false);

        // then
        assertFalse(tlsMutualAuthenticationRequired());
        assertEquals("false", System.getProperty("mockserver.tlsMutualAuthenticationRequired"));
    }

    @Test
    public void shouldSetAndReadTLSMutualAuthenticationCertificateChain() throws IOException {
        // given
        System.clearProperty("mockserver.tlsMutualAuthenticationCertificateChain");

        // then
        assertThat(tlsMutualAuthenticationCertificateChain(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        tlsMutualAuthenticationCertificateChain(tempFile.getAbsolutePath());

        // then
        assertThat(tlsMutualAuthenticationCertificateChain(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.tlsMutualAuthenticationCertificateChain"));
    }

    @Test
    public void shouldSetAndReadForwardProxyTLSX509CertificatesTrustManager() {
        // given
        System.clearProperty("mockserver.forwardProxyTLSX509CertificatesTrustManagerType");

        // then
        assertThat(forwardProxyTLSX509CertificatesTrustManagerType(), is(ForwardProxyTLSX509CertificatesTrustManager.ANY));

        // when
        forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM.name());

        // then
        assertThat(forwardProxyTLSX509CertificatesTrustManagerType(), is(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM));
        assertEquals(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM.name(), System.getProperty("mockserver.forwardProxyTLSX509CertificatesTrustManagerType"));
    }

    @Test
    public void shouldSetAndReadForwardProxyTLSCustomTrustX509Certificates() throws IOException {
        // given
        System.clearProperty("mockserver.forwardProxyTLSCustomTrustX509Certificates");

        // then
        assertThat(forwardProxyTLSCustomTrustX509Certificates(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        forwardProxyTLSCustomTrustX509Certificates(tempFile.getAbsolutePath());

        // then
        assertThat(forwardProxyTLSCustomTrustX509Certificates(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.forwardProxyTLSCustomTrustX509Certificates"));
    }

    @Test
    public void shouldSetAndReadForwardProxyPrivateKey() throws IOException {
        // given
        System.clearProperty("mockserver.forwardProxyPrivateKey");

        // then
        assertThat(forwardProxyPrivateKey(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        forwardProxyPrivateKey(tempFile.getAbsolutePath());

        // then
        assertThat(forwardProxyPrivateKey(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.forwardProxyPrivateKey"));
    }

    @Test
    public void shouldSetAndReadForwardProxyCertificateChain() throws IOException {
        // given
        System.clearProperty("mockserver.forwardProxyCertificateChain");

        // then
        assertThat(forwardProxyCertificateChain(), is(""));

        // when
        File tempFile = File.createTempFile("some", "temp");
        forwardProxyCertificateChain(tempFile.getAbsolutePath());

        // then
        assertThat(forwardProxyCertificateChain(), is(tempFile.getAbsolutePath()));
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.forwardProxyCertificateChain"));
    }

    @Test
    public void shouldSetAndReadLogLevelUsingSLF4J() {
        // SAVE
        String previousValue = logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");

            // when
            assertEquals(Level.INFO, logLevel());
            logLevel("TRACE");

            // then
            assertEquals(Level.TRACE, logLevel());
            assertEquals("FINEST", javaLoggerLogLevel());
            assertEquals("TRACE", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            logLevel(previousValue);
        }
    }

    @Test
    public void shouldSetAndReadLogLevelUsingJavaLogger() {
        // SAVE
        String previousValue = logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");
            logLevel(null);

            // when
            assertEquals(Level.INFO, logLevel());
            logLevel("FINEST");

            // then
            assertEquals(Level.TRACE, logLevel());
            assertEquals("FINEST", javaLoggerLogLevel());
            assertEquals("FINEST", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            logLevel(previousValue);
        }
    }

    @Test
    public void shouldSetAndReadMetricsEnabled() {
        // given
        System.clearProperty("mockserver.metricsEnabled");

        // when
        assertFalse(metricsEnabled());
        metricsEnabled(true);

        // then
        assertTrue(metricsEnabled());
        assertEquals("true", System.getProperty("mockserver.metricsEnabled"));
    }

    @Test
    public void shouldSetAndReadDisableSystemOut() {
        // when
        disableSystemOut(true);

        // then
        assertTrue(disableSystemOut());
        assertEquals("true", System.getProperty("mockserver.disableSystemOut"));

        // when
        disableSystemOut(false);

        // then
        assertFalse(disableSystemOut());
        assertEquals("false", System.getProperty("mockserver.disableSystemOut"));
    }

    @Test
    @Deprecated
    public void shouldSetAndReadHttpProxy() {
        // given
        System.clearProperty("mockserver.httpProxy");

        // when
        assertNull(httpProxy());
        httpProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", httpProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.httpProxy"));
    }

    @Test
    @Deprecated
    public void shouldThrowIllegalArgumentExceptionForInvalidHttpProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid httpProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        httpProxy("abc.def");
    }

    @Test
    @Deprecated
    public void shouldSetAndReadHttpsProxy() {
        // given
        System.clearProperty("mockserver.httpsProxy");

        // when
        assertNull(httpsProxy());
        httpsProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", httpsProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.httpsProxy"));
    }

    @Test
    @Deprecated
    public void shouldThrowIllegalArgumentExceptionForInvalidHttpsProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid httpsProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        httpsProxy("abc.def");
    }

    @Test
    @Deprecated
    public void shouldSetAndReadSocksProxy() {
        // given
        System.clearProperty("mockserver.socksProxy");

        // when
        assertNull(socksProxy());
        socksProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", socksProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.socksProxy"));
    }

    @Test
    @Deprecated
    public void shouldThrowIllegalArgumentExceptionForInvalidSocksProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid socksProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        socksProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardHttpProxy() {
        // given
        System.clearProperty("mockserver.forwardHttpProxy");

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", forwardHttpProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.forwardHttpProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardHttpProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid forwardHttpProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        forwardHttpProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardHttpsProxy() {
        // given
        System.clearProperty("mockserver.forwardHttpsProxy");

        // when
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", forwardHttpsProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.forwardHttpsProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardHttpsProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid forwardHttpsProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        forwardHttpsProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardSocksProxy() {
        // given
        System.clearProperty("mockserver.forwardSocksProxy");

        // when
        assertNull(forwardSocksProxy());
        forwardSocksProxy("127.0.0.1:1080");

        // then
        assertEquals("/127.0.0.1:1080", forwardSocksProxy().toString());
        assertEquals("127.0.0.1:1080", System.getProperty("mockserver.forwardSocksProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardSocksProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid forwardSocksProxy property must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        forwardSocksProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardProxyAuthenticationUsername() {
        // given
        System.clearProperty("mockserver.forwardProxyAuthenticationUsername");

        // when
        assertNull(forwardProxyAuthenticationUsername());
        forwardProxyAuthenticationUsername("foo_bar");

        // then
        assertEquals("foo_bar", forwardProxyAuthenticationUsername());
        assertEquals("foo_bar", System.getProperty("mockserver.forwardProxyAuthenticationUsername"));
    }

    @Test
    public void shouldSetAndReadForwardProxyAuthenticationPassword() {
        // given
        System.clearProperty("mockserver.forwardProxyAuthenticationPassword");

        // when
        assertNull(forwardProxyAuthenticationPassword());
        forwardProxyAuthenticationPassword("bar_foo");

        // then
        assertEquals("bar_foo", forwardProxyAuthenticationPassword());
        assertEquals("bar_foo", System.getProperty("mockserver.forwardProxyAuthenticationPassword"));
    }

    @Test
    public void shouldSetAndReadLocalBoundIP() {
        // given
        System.clearProperty("mockserver.localBoundIP");

        // when
        assertEquals("", localBoundIP());
        localBoundIP("127.0.0.1");

        // then
        assertEquals("127.0.0.1", localBoundIP());
        assertEquals("127.0.0.1", System.getProperty("mockserver.localBoundIP"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidLocalBoundIP() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("'abc.def' is not an IP string literal"));

        localBoundIP("abc.def");
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationRealm() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationRealm");

        // when
        assertEquals("MockServer HTTP Proxy", proxyAuthenticationRealm());
        proxyAuthenticationRealm("my realm");

        // then
        assertEquals("my realm", proxyAuthenticationRealm());
        assertEquals("my realm", System.getProperty("mockserver.proxyAuthenticationRealm"));
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationUsername() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationUsername");

        // when
        assertEquals("", proxyAuthenticationUsername());
        proxyAuthenticationUsername("john.doe");

        // then
        assertEquals("john.doe", proxyAuthenticationUsername());
        assertEquals("john.doe", System.getProperty("mockserver.proxyAuthenticationUsername"));
    }

    @Test
    public void shouldSetAndReadProxyAuthenticationPassword() {
        // given
        System.clearProperty("mockserver.proxyAuthenticationPassword");

        // when
        assertEquals("", proxyAuthenticationPassword());
        proxyAuthenticationPassword("p@ssw0rd");

        // then
        assertEquals("p@ssw0rd", proxyAuthenticationPassword());
        assertEquals("p@ssw0rd", System.getProperty("mockserver.proxyAuthenticationPassword"));
    }

    @Test
    public void shouldSetAndReadInitializationClass() {
        // given
        System.clearProperty("mockserver.initializationClass");

        // when
        assertEquals("", initializationClass());
        initializationClass(ExpectationInitializerExample.class.getName());

        // then
        assertEquals(ExpectationInitializerExample.class.getName(), initializationClass());
        assertEquals(ExpectationInitializerExample.class.getName(), System.getProperty("mockserver.initializationClass"));
    }

    @Test
    public void shouldSetAndReadInitializationJsonPath() {
        // given
        System.clearProperty("mockserver.initializationJsonPath");

        // when
        assertEquals("", initializationJsonPath());
        initializationJsonPath("org/mockserver/server/initialize/initializerJson.json");

        // then
        assertEquals("org/mockserver/server/initialize/initializerJson.json", initializationJsonPath());
        assertEquals("org/mockserver/server/initialize/initializerJson.json", System.getProperty("mockserver.initializationJsonPath"));
    }

    @Test
    public void shouldSetAndReadWatchInitializationJson() {
        // given
        System.clearProperty("mockserver.watchInitializationJson");

        // when
        assertFalse(watchInitializationJson());
        watchInitializationJson(true);

        // then
        assertTrue(watchInitializationJson());
        assertEquals("" + true, System.getProperty("mockserver.watchInitializationJson"));
    }

    @Test
    public void shouldSetAndReadPersistExpectations() {
        try {
            // given
            System.clearProperty("mockserver.persistExpectations");

            // when
            assertFalse(persistExpectations());
            persistExpectations(true);

            // then
            assertTrue(persistExpectations());
            assertEquals("" + true, System.getProperty("mockserver.persistExpectations"));
        } finally {
            System.clearProperty("mockserver.persistExpectations");
        }
    }

    @Test
    public void shouldSetAndReadPersistedExpectationsPath() {
        try {
            // given
            System.clearProperty("mockserver.persistedExpectationsPath");

            // when
            assertEquals("persistedExpectations.json", persistedExpectationsPath());
            persistedExpectationsPath("otherPersistedExpectations.json");

            // then
            assertEquals("otherPersistedExpectations.json", persistedExpectationsPath());
            assertEquals("otherPersistedExpectations.json", System.getProperty("mockserver.persistedExpectationsPath"));
        } finally {
            System.clearProperty("mockserver.persistedExpectationsPath");
        }
    }

    @Test
    public void shouldSetAndReadEnableCORSSettingForAPI() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");
        reset();

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
        reset();

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
    public void shouldSetAndReadCORSAllowHeaders() {
        // given
        System.clearProperty("mockserver.corsAllowHeaders");

        // when
        assertEquals("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization", corsAllowHeaders());
        corsAllowHeaders("RandomHeader, AnotherHeader");

        // then
        assertEquals("RandomHeader, AnotherHeader", corsAllowHeaders());
        assertEquals("RandomHeader, AnotherHeader", System.getProperty("mockserver.corsAllowHeaders"));
    }

    @Test
    public void shouldSetAndReadCORSAllowMethods() {
        // given
        System.clearProperty("mockserver.corsAllowMethods");

        // when
        assertEquals("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE", corsAllowMethods());
        corsAllowMethods("CONNECT, PATCH");

        // then
        assertEquals("CONNECT, PATCH", corsAllowMethods());
        assertEquals("CONNECT, PATCH", System.getProperty("mockserver.corsAllowMethods"));
    }

    @Test
    public void shouldSetAndReadCORSAllowCredentials() {
        // given
        System.clearProperty("mockserver.corsAllowCredentials");

        // when
        assertTrue(corsAllowCredentials());
        corsAllowCredentials(false);

        // then
        assertFalse(corsAllowCredentials());
        assertEquals("" + false, System.getProperty("mockserver.corsAllowCredentials"));
    }

    @Test
    public void shouldSetAndReadCORSMaxAgeInSeconds() {
        // given
        System.clearProperty("mockserver.corsMaxAgeInSeconds");

        // when
        assertEquals(300, corsMaxAgeInSeconds());
        corsMaxAgeInSeconds(100);

        // then
        assertEquals("100", System.getProperty("mockserver.corsMaxAgeInSeconds"));
        assertEquals(100, corsMaxAgeInSeconds());
    }

    @Test
    public void shouldSetAndReadLivenessHttpGetPath() {
        // given
        System.clearProperty("mockserver.livenessHttpGetPath");

        // when
        assertEquals("", livenessHttpGetPath());
        livenessHttpGetPath("/livenessHttpGetPath");

        // then
        assertEquals("/livenessHttpGetPath", livenessHttpGetPath());
        assertEquals("/livenessHttpGetPath", System.getProperty("mockserver.livenessHttpGetPath"));
    }
}
