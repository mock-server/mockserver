package org.mockserver.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.server.initialize.ExpectationInitializerExample;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
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
        ConfigurationProperties.resetAllSystemProperties();
    }

    @Test
    public void shouldRemoveLeadingAndTrailingQuotes() {
        // given
        System.clearProperty("mockserver.initializationJsonPath");

        // when - leading and trailing
        assertEquals("", initializationJsonPath());
        initializationJsonPath("\"org/mockserver/server/initialize/initializerJson.json\"");

        // then
        assertEquals("org/mockserver/server/initialize/initializerJson.json", initializationJsonPath());
        assertEquals("\"org/mockserver/server/initialize/initializerJson.json\"", System.getProperty("mockserver.initializationJsonPath"));

        // when - only trailing
        System.clearProperty("mockserver.initializationJsonPath");
        assertEquals("", initializationJsonPath());
        initializationJsonPath("org/mockserver/server/initialize/initializerJson.json\"");

        // then
        assertEquals("org/mockserver/server/initialize/initializerJson.json\"", initializationJsonPath());
        assertEquals("org/mockserver/server/initialize/initializerJson.json\"", System.getProperty("mockserver.initializationJsonPath"));

        // when - only leading
        System.clearProperty("mockserver.initializationJsonPath");
        assertEquals("", initializationJsonPath());
        initializationJsonPath("\"org/mockserver/server/initialize/initializerJson.json");

        // then
        assertEquals("\"org/mockserver/server/initialize/initializerJson.json", initializationJsonPath());
        assertEquals("\"org/mockserver/server/initialize/initializerJson.json", System.getProperty("mockserver.initializationJsonPath"));
    }

    @Test
    public void shouldSetAndReadNioEventLoopThreadCount() {
        // given
        System.clearProperty("mockserver.nioEventLoopThreadCount");

        // when
        assertEquals(5, nioEventLoopThreadCount());
        nioEventLoopThreadCount(2);

        // then
        assertEquals("2", System.getProperty("mockserver.nioEventLoopThreadCount"));
        assertEquals(2, nioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadClientNioEventLoopThreadCount() {
        // given
        System.clearProperty("mockserver.clientNioEventLoopThreadCount");

        // when
        assertEquals(5, clientNioEventLoopThreadCount());
        clientNioEventLoopThreadCount(2);

        // then
        assertEquals("2", System.getProperty("mockserver.clientNioEventLoopThreadCount"));
        assertEquals(2, clientNioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadActionHandlerThreadCount() {
        // given
        System.clearProperty("mockserver.actionHandlerThreadCount");
        int actionHandlerThreadCount = Math.max(5, Runtime.getRuntime().availableProcessors());

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
    public void shouldHandleInvalidNioEventLoopThreadCount() {
        // given
        System.setProperty("mockserver.nioEventLoopThreadCount", "invalid");

        // then
        assertEquals(5, nioEventLoopThreadCount());
    }

    @Test
    public void shouldHandleInvalidClientNioEventLoopThreadCount() {
        // given
        System.setProperty("mockserver.clientNioEventLoopThreadCount", "invalid");

        // then
        assertEquals(5, clientNioEventLoopThreadCount());
    }

    @Test
    public void shouldSetAndReadMaxExpectations() {
        // given
        System.clearProperty("mockserver.maxExpectations");

        // when
        assertEquals(memoryMonitoring.startingMaxExpectations(), maxExpectations());
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
        assertEquals(memoryMonitoring.startingMaxExpectations(), maxExpectations());
    }

    @Test
    public void shouldSetAndReadRequestLogSize() {
        // given
        System.clearProperty("mockserver.maxLogEntries");

        // when
        assertEquals(memoryMonitoring.startingMaxLogEntries(), maxLogEntries());
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
        assertEquals(memoryMonitoring.startingMaxLogEntries(), maxLogEntries());
    }

    @Test
    public void shouldSetAndReadOutputMemoryUsageCsv() {
        // given
        boolean originalSetting = outputMemoryUsageCsv();
        try {
            // when
            outputMemoryUsageCsv(true);

            // then
            assertTrue(outputMemoryUsageCsv());
            assertEquals("true", System.getProperty("mockserver.outputMemoryUsageCsv"));

            // when
            outputMemoryUsageCsv(false);

            // then
            assertFalse(outputMemoryUsageCsv());
            assertEquals("false", System.getProperty("mockserver.outputMemoryUsageCsv"));
        } finally {
            outputMemoryUsageCsv(originalSetting);
        }
    }

    @Test
    public void shouldSetAndReadMemoryUsageCsvDirectory() throws IOException {
        // given
        System.clearProperty("mockserver.memoryUsageCsvDirectory");

        // when
        assertEquals(".", memoryUsageCsvDirectory());
        File tempFile = File.createTempFile("prefix", "suffix");
        memoryUsageCsvDirectory(tempFile.getAbsolutePath());

        // then
        assertEquals(tempFile.getAbsolutePath(), memoryUsageCsvDirectory());
        assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.memoryUsageCsvDirectory"));
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
    public void shouldSetAndReadUseSemicolonAsQueryParameterSeparator() {
        // given
        System.clearProperty("mockserver.useSemicolonAsQueryParameterSeparator");

        // when
        assertTrue(useSemicolonAsQueryParameterSeparator());
        useSemicolonAsQueryParameterSeparator(false);

        // then
        assertFalse(useSemicolonAsQueryParameterSeparator());
        assertEquals("false", System.getProperty("mockserver.useSemicolonAsQueryParameterSeparator"));
    }

    @Test
    public void shouldSetAndReadSslCertificateDomainName() {
        String originalSslCertificateDomainName = sslCertificateDomainName();
        try {
            // given
            System.clearProperty("mockserver.sslCertificateDomainName");

            // when
            assertEquals(KeyAndCertificateFactory.CERTIFICATE_DOMAIN, sslCertificateDomainName());
            sslCertificateDomainName("newDomain");

            // then
            assertEquals("newDomain", sslCertificateDomainName());
            assertEquals("newDomain", System.getProperty("mockserver.sslCertificateDomainName"));
        } finally {
            sslCertificateDomainName(originalSslCertificateDomainName);
        }
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameDomains() {
        Set<String> originalSslSubjectAlternativeNameDomains = sslSubjectAlternativeNameDomains();
        try {
            // given
            System.clearProperty("mockserver.sslSubjectAlternativeNameDomains");

            // when
            assertEquals(ImmutableSet.of("localhost"), sslSubjectAlternativeNameDomains());
            sslSubjectAlternativeNameDomains(ImmutableSet.of("a", "b", "c", "d"));

            // then
            assertEquals(ImmutableSet.of("a", "b", "c", "d"), sslSubjectAlternativeNameDomains());
            assertEquals("a,b,c,d", System.getProperty("mockserver.sslSubjectAlternativeNameDomains"));
        } finally {
            sslSubjectAlternativeNameDomains(originalSslSubjectAlternativeNameDomains);
        }
    }

    @Test
    public void shouldSetAndReadSslSubjectAlternativeNameIps() {
        Set<String> originalSslSubjectAlternativeNameIps = sslSubjectAlternativeNameIps();
        try {
            // given
            System.clearProperty("mockserver.sslSubjectAlternativeNameIps");

            // when
            assertEquals(ImmutableSet.of("127.0.0.1", "0.0.0.0"), sslSubjectAlternativeNameIps());
            sslSubjectAlternativeNameIps(ImmutableSet.of("1.2.3.4", "5.6.7.8"));

            // then
            assertEquals(ImmutableSet.of("1.2.3.4", "5.6.7.8"), sslSubjectAlternativeNameIps());
            assertEquals("1.2.3.4,5.6.7.8", System.getProperty("mockserver.sslSubjectAlternativeNameIps"));
        } finally {
            sslSubjectAlternativeNameIps(originalSslSubjectAlternativeNameIps);
        }
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
    public void shouldSetAndReadProactivelyInitialiseTLS() {
        // given
        System.clearProperty("mockserver.proactivelyInitialiseTLS");

        // when
        assertFalse(proactivelyInitialiseTLS());
        proactivelyInitialiseTLS(true);

        // then
        assertTrue(proactivelyInitialiseTLS());
        assertEquals("true", System.getProperty("mockserver.proactivelyInitialiseTLS"));
    }

    @Test
    public void shouldSetAndReadPreventCertificateDynamicUpdate() {
        // given
        System.clearProperty("mockserver.preventCertificateDynamicUpdate");

        // when
        assertFalse(preventCertificateDynamicUpdate());
        preventCertificateDynamicUpdate(true);

        // then
        assertTrue(preventCertificateDynamicUpdate());
        assertEquals("true", System.getProperty("mockserver.preventCertificateDynamicUpdate"));
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
        String originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        try {
            // given
            System.clearProperty("mockserver.certificateAuthorityCertificate");

            // when
            assertEquals("org/mockserver/socket/CertificateAuthorityCertificate.pem", certificateAuthorityCertificate());
            File tempFile = File.createTempFile("prefix", "suffix");
            certificateAuthorityCertificate(tempFile.getAbsolutePath());

            // then
            assertEquals(tempFile.getAbsolutePath(), certificateAuthorityCertificate());
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.certificateAuthorityCertificate"));
        } finally {
            certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        }
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
        assertThat(directoryToSaveDynamicSSLCertificate(), is("."));
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
    public void shouldSetAndReadControlPlaneTLSMutualAuthenticationRequired() {
        boolean originalControlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneTLSMutualAuthenticationRequired");

            // then
            assertFalse(controlPlaneTLSMutualAuthenticationRequired());

            // when
            controlPlaneTLSMutualAuthenticationRequired(true);

            // then
            assertTrue(controlPlaneTLSMutualAuthenticationRequired());
            assertEquals("true", System.getProperty("mockserver.controlPlaneTLSMutualAuthenticationRequired"));

            // when
            controlPlaneTLSMutualAuthenticationRequired(false);

            // then
            assertFalse(controlPlaneTLSMutualAuthenticationRequired());
            assertEquals("false", System.getProperty("mockserver.controlPlaneTLSMutualAuthenticationRequired"));
        } finally {
            controlPlaneTLSMutualAuthenticationRequired(originalControlPlaneTLSMutualAuthenticationRequired);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneTLSMutualAuthenticationCAChain() throws IOException {
        String originalControlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneTLSMutualAuthenticationCAChain");

            // then
            assertThat(controlPlaneTLSMutualAuthenticationCAChain(), is(""));

            // when
            File tempFile = File.createTempFile("some", "temp");
            controlPlaneTLSMutualAuthenticationCAChain(tempFile.getAbsolutePath());

            // then
            assertThat(controlPlaneTLSMutualAuthenticationCAChain(), is(tempFile.getAbsolutePath()));
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.controlPlaneTLSMutualAuthenticationCAChain"));
        } finally {
            controlPlaneTLSMutualAuthenticationCAChain(originalControlPlaneTLSMutualAuthenticationCAChain);
        }
    }

    @Test
    public void shouldSetAndReadControlPlanePrivateKeyPath() throws IOException {
        String originalControlPlanePrivateKeyPath = controlPlanePrivateKeyPath();
        try {
            // given
            System.clearProperty("mockserver.controlPlanePrivateKeyPath");

            // then
            assertThat(controlPlanePrivateKeyPath(), is(""));

            // when
            File tempFile = File.createTempFile("some", "temp");
            controlPlanePrivateKeyPath(tempFile.getAbsolutePath());

            // then
            assertThat(controlPlanePrivateKeyPath(), is(tempFile.getAbsolutePath()));
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.controlPlanePrivateKeyPath"));
        } finally {
            controlPlanePrivateKeyPath(originalControlPlanePrivateKeyPath);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneX509CertificatePath() throws IOException {
        String originalControlPlaneX509CertificatePath = controlPlaneX509CertificatePath();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneX509CertificatePath");

            // then
            assertThat(controlPlaneX509CertificatePath(), is(""));

            // when
            File tempFile = File.createTempFile("some", "temp");
            controlPlaneX509CertificatePath(tempFile.getAbsolutePath());

            // then
            assertThat(controlPlaneX509CertificatePath(), is(tempFile.getAbsolutePath()));
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.controlPlaneX509CertificatePath"));
        } finally {
            controlPlaneX509CertificatePath(originalControlPlaneX509CertificatePath);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneJWTAuthenticationRequired() {
        boolean originalControlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneJWTAuthenticationRequired");

            // then
            assertFalse(controlPlaneJWTAuthenticationRequired());

            // when
            controlPlaneJWTAuthenticationRequired(true);

            // then
            assertTrue(controlPlaneJWTAuthenticationRequired());
            assertEquals("true", System.getProperty("mockserver.controlPlaneJWTAuthenticationRequired"));

            // when
            controlPlaneJWTAuthenticationRequired(false);

            // then
            assertFalse(controlPlaneJWTAuthenticationRequired());
            assertEquals("false", System.getProperty("mockserver.controlPlaneJWTAuthenticationRequired"));
        } finally {
            controlPlaneJWTAuthenticationRequired(originalControlPlaneJWTAuthenticationRequired);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneJWTAuthenticationJWKSource() throws IOException {
        String originalControlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneJWTAuthenticationJWKSource");

            // then
            assertThat(controlPlaneJWTAuthenticationJWKSource(), is(""));

            // when
            File tempFile = File.createTempFile("some", "temp");
            controlPlaneJWTAuthenticationJWKSource(tempFile.getAbsolutePath());

            // then
            assertThat(controlPlaneJWTAuthenticationJWKSource(), is(tempFile.getAbsolutePath()));
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.controlPlaneJWTAuthenticationJWKSource"));
        } finally {
            controlPlaneJWTAuthenticationJWKSource(originalControlPlaneJWTAuthenticationJWKSource);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneJWTAuthenticationExpectedAudience() throws IOException {
        String originalControlPlaneJWTAuthenticationExpectedAudience = controlPlaneJWTAuthenticationExpectedAudience();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneJWTAuthenticationExpectedAudience");

            // then
            assertThat(controlPlaneJWTAuthenticationExpectedAudience(), is(""));

            // when
            File tempFile = File.createTempFile("some", "temp");
            controlPlaneJWTAuthenticationExpectedAudience(tempFile.getAbsolutePath());

            // then
            assertThat(controlPlaneJWTAuthenticationExpectedAudience(), is(tempFile.getAbsolutePath()));
            assertEquals(tempFile.getAbsolutePath(), System.getProperty("mockserver.controlPlaneJWTAuthenticationExpectedAudience"));
        } finally {
            controlPlaneJWTAuthenticationExpectedAudience(originalControlPlaneJWTAuthenticationExpectedAudience);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneJWTAuthenticationMatchingClaims() {
        Map<String, String> originalControlPlaneJWTAuthenticationMatchingClaims = controlPlaneJWTAuthenticationMatchingClaims();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneJWTAuthenticationMatchingClaims");

            // then
            assertThat(controlPlaneJWTAuthenticationMatchingClaims(), is(ImmutableMap.of()));

            // when
            controlPlaneJWTAuthenticationMatchingClaims(ImmutableMap.of("a", "b", "c", "d"));

            // then
            assertThat(controlPlaneJWTAuthenticationMatchingClaims(), allOf(hasEntry("a", "b"), hasEntry("c", "d")));
            assertEquals("a=b,c=d", System.getProperty("mockserver.controlPlaneJWTAuthenticationMatchingClaims"));
        } finally {
            controlPlaneJWTAuthenticationMatchingClaims(originalControlPlaneJWTAuthenticationMatchingClaims);
        }
    }

    @Test
    public void shouldSetAndReadControlPlaneJWTAuthenticationRequiredClaims() {
        Set<String> originalControlPlaneJWTAuthenticationRequiredClaims = controlPlaneJWTAuthenticationRequiredClaims();
        try {
            // given
            System.clearProperty("mockserver.controlPlaneJWTAuthenticationRequiredClaims");

            // then
            assertThat(controlPlaneJWTAuthenticationRequiredClaims(), is(ImmutableSet.of("")));

            // when
            controlPlaneJWTAuthenticationRequiredClaims(ImmutableSet.of("a", "b"));

            // then
            assertThat(controlPlaneJWTAuthenticationRequiredClaims(), is(ImmutableSet.of("a", "b")));
            assertEquals("a,b", System.getProperty("mockserver.controlPlaneJWTAuthenticationRequiredClaims"));
        } finally {
            controlPlaneJWTAuthenticationRequiredClaims(originalControlPlaneJWTAuthenticationRequiredClaims);
        }
    }

    @Test
    public void shouldSetAndReadLogLevelUsingSLF4J() {
        // SAVE
        String originalLogLevel = logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");
            ConfigurationProperties.resetAllSystemProperties();
            logLevel("INFO");

            // when
            assertEquals(Level.INFO, logLevel());
            logLevel("TRACE");

            // then
            assertEquals(Level.TRACE, logLevel());
            assertEquals("FINEST", javaLoggerLogLevel());
            assertEquals("TRACE", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            logLevel(originalLogLevel);
        }
    }

    @Test
    public void shouldSetAndReadLogLevelUsingJavaLogger() {
        // SAVE
        String originalLogLevel = logLevel().name();
        try {
            // given
            System.clearProperty("mockserver.logLevel");
            ConfigurationProperties.resetAllSystemProperties();
            logLevel("INFO");

            // when
            assertEquals(Level.INFO, logLevel());
            logLevel("FINEST");

            // then
            assertEquals(Level.TRACE, logLevel());
            assertEquals("FINEST", javaLoggerLogLevel());
            assertEquals("FINEST", System.getProperty("mockserver.logLevel"));
        } finally {
            // RESET
            logLevel(originalLogLevel);
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
        boolean originalSetting = disableSystemOut();
        try {
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
        } finally {
            disableSystemOut(originalSetting);
        }
    }

    @Test
    public void shouldSetAndReadDisableLogging() {
        boolean originalSetting = disableLogging();
        try {
            // when
            disableLogging(true);

            // then
            assertTrue(disableLogging());
            assertEquals("true", System.getProperty("mockserver.disableLogging"));

            // when
            disableLogging(false);

            // then
            assertFalse(disableLogging());
            assertEquals("false", System.getProperty("mockserver.disableLogging"));
        } finally {
            disableLogging(originalSetting);
        }
    }

    @Test
    public void shouldSetAndReadDetailedMatchFailures() {
        boolean originalSetting = detailedMatchFailures();
        try {
            // when
            detailedMatchFailures(true);

            // then
            assertTrue(detailedMatchFailures());
            assertEquals("true", System.getProperty("mockserver.detailedMatchFailures"));

            // when
            detailedMatchFailures(false);

            // then
            assertFalse(detailedMatchFailures());
            assertEquals("false", System.getProperty("mockserver.detailedMatchFailures"));
        } finally {
            detailedMatchFailures(originalSetting);
        }
    }

    @Test
    public void shouldSetAndReadLaunchUIForLogLevelDebug() {
        boolean originalSetting = launchUIForLogLevelDebug();
        try {
            // when
            launchUIForLogLevelDebug(true);

            // then
            assertTrue(launchUIForLogLevelDebug());
            assertEquals("true", System.getProperty("mockserver.launchUIForLogLevelDebug"));

            // when
            launchUIForLogLevelDebug(false);

            // then
            assertFalse(launchUIForLogLevelDebug());
            assertEquals("false", System.getProperty("mockserver.launchUIForLogLevelDebug"));
        } finally {
            launchUIForLogLevelDebug(originalSetting);
        }
    }

    @Test
    public void shouldSetAndReadMatchersFailFast() {
        boolean originalSetting = matchersFailFast();
        try {
            // when
            matchersFailFast(true);

            // then
            assertTrue(matchersFailFast());
            assertEquals("true", System.getProperty("mockserver.matchersFailFast"));

            // when
            matchersFailFast(false);

            // then
            assertFalse(matchersFailFast());
            assertEquals("false", System.getProperty("mockserver.matchersFailFast"));
        } finally {
            matchersFailFast(originalSetting);
        }
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
    public void shouldSetAndReadAttemptToProxyIfNoMatchingExpectation() {
        // given
        System.clearProperty("mockserver.attemptToProxyIfNoMatchingExpectation");

        // when
        assertTrue(attemptToProxyIfNoMatchingExpectation());
        attemptToProxyIfNoMatchingExpectation(false);

        // then
        assertFalse(attemptToProxyIfNoMatchingExpectation());
        assertEquals("false", System.getProperty("mockserver.attemptToProxyIfNoMatchingExpectation"));
    }

    @Test
    public void shouldSetAndReadForwardHttpProxy() {
        // given
        System.clearProperty("mockserver.forwardHttpProxy");
        String httpProxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy(httpProxyAddress);

        // then
        assertEquals("/" + httpProxyAddress, forwardHttpProxy().toString());
        assertEquals(httpProxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardHttpProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid property \"mockserver.forwardHttpProxy\" must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        forwardHttpProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardHttpsProxy() {
        // given
        System.clearProperty("mockserver.forwardHttpsProxy");
        String httpProxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(httpProxyAddress);

        // then
        assertEquals("/" + httpProxyAddress, forwardHttpsProxy().toString());
        assertEquals(httpProxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardHttpsProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid property \"mockserver.forwardHttpsProxy\" must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

        forwardHttpsProxy("abc.def");
    }

    @Test
    public void shouldSetAndReadForwardSocksProxy() {
        // given
        System.clearProperty("mockserver.forwardSocksProxy");
        String httpProxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardSocksProxy());
        forwardSocksProxy(httpProxyAddress);

        // then
        assertEquals("/" + httpProxyAddress, forwardSocksProxy().toString());
        assertEquals(httpProxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionForInvalidForwardSocksProxy() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Invalid property \"mockserver.forwardSocksProxy\" must include <host>:<port> for example \"127.0.0.1:1090\" or \"localhost:1090\""));

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
    public void shouldSetAndReadMaximumNumberOfRequestToReturnInVerificationFailure() {
        Integer originalMaximumNumberOfRequestToReturnInVerificationFailure = maximumNumberOfRequestToReturnInVerificationFailure();
        try {
            // given
            System.clearProperty("mockserver.maximumNumberOfRequestToReturnInVerificationFailure");

            // when
            assertThat(maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(10));
            maximumNumberOfRequestToReturnInVerificationFailure(1);

            // then
            assertEquals("1", System.getProperty("mockserver.maximumNumberOfRequestToReturnInVerificationFailure"));
            assertThat(maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(1));
        } finally {
            maximumNumberOfRequestToReturnInVerificationFailure(originalMaximumNumberOfRequestToReturnInVerificationFailure);
        }
    }

    @Test
    public void shouldSetAndReadEnableCORSSettingForAPI() {
        // given
        System.clearProperty("mockserver.enableCORSForAPI");
        resetAllSystemProperties();

        // when
        assertFalse(ConfigurationProperties.enableCORSForAPI());
        ConfigurationProperties.enableCORSForAPI(true);

        // then
        assertTrue(ConfigurationProperties.enableCORSForAPI());
        assertEquals("true", System.getProperty("mockserver.enableCORSForAPI"));
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
