package org.mockserver.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.server.initialize.ExpectationInitializerExample;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.KeyAndCertificateFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;

public class ConfigurationTest {

    private Configuration configuration;

    @Before
    public void setupTest() {
        configuration = new Configuration();
    }

    private String tempFilePath() {
        try {
            return File.createTempFile("prefix", "suffix").getAbsolutePath();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }

    @Test
    public void shouldSetAndGetLogLevel() {
        String original = ConfigurationProperties.logLevel().name();
        try {
            // then - default value
            assertThat(configuration.logLevel().name(), anyOf(equalTo("INFO"), equalTo("ERROR")));

            // when - system property setter
            ConfigurationProperties.logLevel("TRACE");

            // then - system property getter
            assertThat(ConfigurationProperties.logLevel().name(), equalTo("TRACE"));
            assertThat(System.getProperty("mockserver.logLevel"), equalTo("TRACE"));
            assertThat(configuration.logLevel().name(), equalTo("TRACE"));

            // when - setter
            configuration.logLevel("DEBUG");

            // then - getter
            assertThat(configuration.logLevel().name(), equalTo("DEBUG"));

            // then - validate
            IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> logLevel("WRONG"));
            assertThat(illegalArgumentException.getMessage(), is("log level \"WRONG\" is not legal it must be one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\", or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\""));
        } finally {
            ConfigurationProperties.logLevel(original);
        }
    }

    @Test
    public void shouldSetAndGetDisableSystemOut() {
        boolean original = ConfigurationProperties.disableSystemOut();
        try {
            // then - default value
            assertThat(configuration.disableSystemOut(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.disableSystemOut(true);

            // then - system property getter
            assertThat(ConfigurationProperties.disableSystemOut(), equalTo(true));
            assertThat(System.getProperty("mockserver.disableSystemOut"), equalTo("true"));
            assertThat(configuration.disableSystemOut(), equalTo(true));
            ConfigurationProperties.disableSystemOut(original);

            // when - setter
            configuration.disableSystemOut(true);

            // then - getter
            assertThat(configuration.disableSystemOut(), equalTo(true));
        } finally {
            ConfigurationProperties.disableSystemOut(original);
        }
    }

    @Test
    public void shouldSetAndGetDisableLogging() {
        boolean original = ConfigurationProperties.disableLogging();
        try {
            // then - default value
            assertThat(configuration.disableLogging(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.disableLogging(true);

            // then - system property getter
            assertThat(ConfigurationProperties.disableLogging(), equalTo(true));
            assertThat(System.getProperty("mockserver.disableLogging"), equalTo("true"));
            assertThat(configuration.disableLogging(), equalTo(true));
            ConfigurationProperties.disableLogging(original);

            // when - setter
            configuration.disableLogging(true);

            // then - getter
            assertThat(configuration.disableLogging(), equalTo(true));
        } finally {
            ConfigurationProperties.disableLogging(original);
        }
    }

    @Test
    public void shouldSetAndGetDetailedMatchFailures() {
        boolean original = ConfigurationProperties.detailedMatchFailures();
        try {
            // then - default value
            assertThat(configuration.detailedMatchFailures(), equalTo(true));

            // when - system property setter
            ConfigurationProperties.detailedMatchFailures(false);

            // then - system property getter
            assertThat(ConfigurationProperties.detailedMatchFailures(), equalTo(false));
            assertThat(System.getProperty("mockserver.detailedMatchFailures"), equalTo("false"));
            assertThat(configuration.detailedMatchFailures(), equalTo(false));
            ConfigurationProperties.detailedMatchFailures(original);

            // when - setter
            configuration.detailedMatchFailures(false);

            // then - getter
            assertThat(configuration.detailedMatchFailures(), equalTo(false));
        } finally {
            ConfigurationProperties.detailedMatchFailures(original);
        }
    }

    @Test
    public void shouldSetAndGetLaunchUIForLogLevelDebug() {
        boolean original = ConfigurationProperties.launchUIForLogLevelDebug();
        try {
            // then - default value
            assertThat(configuration.launchUIForLogLevelDebug(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.launchUIForLogLevelDebug(true);

            // then - system property getter
            assertThat(ConfigurationProperties.launchUIForLogLevelDebug(), equalTo(true));
            assertThat(System.getProperty("mockserver.launchUIForLogLevelDebug"), equalTo("true"));
            assertThat(configuration.launchUIForLogLevelDebug(), equalTo(true));
            ConfigurationProperties.launchUIForLogLevelDebug(original);

            // when - setter
            configuration.launchUIForLogLevelDebug(true);

            // then - getter
            assertThat(configuration.launchUIForLogLevelDebug(), equalTo(true));
        } finally {
            ConfigurationProperties.launchUIForLogLevelDebug(original);
        }
    }

    @Test
    public void shouldSetAndGetMetricsEnabled() {
        boolean original = ConfigurationProperties.metricsEnabled();
        try {
            // then - default value
            assertThat(configuration.metricsEnabled(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.metricsEnabled(true);

            // then - system property getter
            assertThat(ConfigurationProperties.metricsEnabled(), equalTo(true));
            assertThat(System.getProperty("mockserver.metricsEnabled"), equalTo("true"));
            assertThat(configuration.metricsEnabled(), equalTo(true));
            ConfigurationProperties.metricsEnabled(original);

            // when - setter
            configuration.metricsEnabled(true);

            // then - getter
            assertThat(configuration.metricsEnabled(), equalTo(true));
        } finally {
            ConfigurationProperties.metricsEnabled(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxExpectations() {
        int original = ConfigurationProperties.maxExpectations();
        try {
            // when - system property setter
            ConfigurationProperties.maxExpectations(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxExpectations(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxExpectations"), equalTo("10"));
            assertThat(configuration.maxExpectations(), equalTo(10));

            // when - setter
            configuration.maxExpectations(20);

            // then - getter
            assertThat(configuration.maxExpectations(), equalTo(20));
        } finally {
            ConfigurationProperties.maxExpectations(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxLogEntries() {
        int original = ConfigurationProperties.maxLogEntries();
        try {
            // when - system property setter
            ConfigurationProperties.maxLogEntries(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxLogEntries(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxLogEntries"), equalTo("10"));
            assertThat(configuration.maxLogEntries(), equalTo(10));

            // when - setter
            configuration.maxLogEntries(20);

            // then - getters
            assertThat(configuration.maxLogEntries(), equalTo(20));
            assertThat(configuration.ringBufferSize(), equalTo(32));

            // when - setter
            configuration.maxLogEntries(100);

            // then - ring buffer size
            assertThat(configuration.ringBufferSize(), equalTo(128));

            // when - setter
            configuration.maxLogEntries(1000);

            // then - ring buffer size
            assertThat(configuration.ringBufferSize(), equalTo(1024));
        } finally {
            ConfigurationProperties.maxLogEntries(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxWebSocketExpectations() {
        int original = ConfigurationProperties.maxWebSocketExpectations();
        try {
            // then - default value
            assertThat(configuration.maxWebSocketExpectations(), equalTo(1500));

            // when - system property setter
            ConfigurationProperties.maxWebSocketExpectations(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxWebSocketExpectations(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxWebSocketExpectations"), equalTo("10"));
            assertThat(configuration.maxWebSocketExpectations(), equalTo(10));

            // when - setter
            configuration.maxWebSocketExpectations(20);

            // then - getter
            assertThat(configuration.maxWebSocketExpectations(), equalTo(20));
        } finally {
            ConfigurationProperties.maxWebSocketExpectations(original);
        }
    }

    @Test
    public void shouldSetAndGetOutputMemoryUsageCsv() {
        boolean original = ConfigurationProperties.outputMemoryUsageCsv();
        try {
            // then - default value
            assertThat(configuration.outputMemoryUsageCsv(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.outputMemoryUsageCsv(true);

            // then - system property getter
            assertThat(ConfigurationProperties.outputMemoryUsageCsv(), equalTo(true));
            assertThat(System.getProperty("mockserver.outputMemoryUsageCsv"), equalTo("true"));
            assertThat(configuration.outputMemoryUsageCsv(), equalTo(true));
            ConfigurationProperties.outputMemoryUsageCsv(original);

            // when - setter
            configuration.outputMemoryUsageCsv(true);

            // then - getter
            assertThat(configuration.outputMemoryUsageCsv(), equalTo(true));
        } finally {
            ConfigurationProperties.outputMemoryUsageCsv(original);
        }
    }

    @Test
    public void shouldSetAndGetMemoryUsageCsvDirectory() {
        String original = ConfigurationProperties.memoryUsageCsvDirectory();
        try {
            // then - default value
            assertThat(configuration.memoryUsageCsvDirectory(), equalTo("."));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.memoryUsageCsvDirectory(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.memoryUsageCsvDirectory(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.memoryUsageCsvDirectory"), equalTo(firstPath));
            assertThat(configuration.memoryUsageCsvDirectory(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.memoryUsageCsvDirectory(secondPath);

            // then - getter
            assertThat(configuration.memoryUsageCsvDirectory(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.memoryUsageCsvDirectory(original);
        }
    }

    @Test
    public void shouldSetAndGetNioEventLoopThreadCount() {
        int original = ConfigurationProperties.nioEventLoopThreadCount();
        try {
            // then - default value
            assertThat(configuration.nioEventLoopThreadCount(), equalTo(5));

            // when - system property setter
            ConfigurationProperties.nioEventLoopThreadCount(10);

            // then - system property getter
            assertThat(ConfigurationProperties.nioEventLoopThreadCount(), equalTo(10));
            assertThat(System.getProperty("mockserver.nioEventLoopThreadCount"), equalTo("10"));
            assertThat(configuration.nioEventLoopThreadCount(), equalTo(10));

            // when - setter
            configuration.nioEventLoopThreadCount(20);

            // then - getter
            assertThat(configuration.nioEventLoopThreadCount(), equalTo(20));
        } finally {
            ConfigurationProperties.nioEventLoopThreadCount(original);
        }
    }

    @Test
    public void shouldSetAndGetActionHandlerThreadCount() {
        int original = ConfigurationProperties.actionHandlerThreadCount();
        try {
            // then - default value
            assertThat(configuration.actionHandlerThreadCount(), equalTo(Math.max(5, Runtime.getRuntime().availableProcessors())));

            // when - system property setter
            ConfigurationProperties.actionHandlerThreadCount(10);

            // then - system property getter
            assertThat(ConfigurationProperties.actionHandlerThreadCount(), equalTo(10));
            assertThat(System.getProperty("mockserver.actionHandlerThreadCount"), equalTo("10"));
            assertThat(configuration.actionHandlerThreadCount(), equalTo(10));

            // when - setter
            configuration.actionHandlerThreadCount(20);

            // then - getter
            assertThat(configuration.actionHandlerThreadCount(), equalTo(20));
        } finally {
            ConfigurationProperties.actionHandlerThreadCount(original);
        }
    }

    @Test
    public void shouldSetAndGetWebSocketClientEventLoopThreadCount() {
        int original = ConfigurationProperties.webSocketClientEventLoopThreadCount();
        try {
            // then - default value
            assertThat(configuration.webSocketClientEventLoopThreadCount(), equalTo(5));

            // when - system property setter
            ConfigurationProperties.webSocketClientEventLoopThreadCount(10);

            // then - system property getter
            assertThat(ConfigurationProperties.webSocketClientEventLoopThreadCount(), equalTo(10));
            assertThat(System.getProperty("mockserver.webSocketClientEventLoopThreadCount"), equalTo("10"));
            assertThat(configuration.webSocketClientEventLoopThreadCount(), equalTo(10));

            // when - setter
            configuration.webSocketClientEventLoopThreadCount(20);

            // then - getter
            assertThat(configuration.webSocketClientEventLoopThreadCount(), equalTo(20));
        } finally {
            ConfigurationProperties.webSocketClientEventLoopThreadCount(original);
        }
    }

    @Test
    public void shouldSetAndGetClientNioEventLoopThreadCount() {
        int original = ConfigurationProperties.clientNioEventLoopThreadCount();
        try {
            // then - default value
            assertThat(configuration.clientNioEventLoopThreadCount(), equalTo(5));

            // when - system property setter
            ConfigurationProperties.clientNioEventLoopThreadCount(10);

            // then - system property getter
            assertThat(ConfigurationProperties.clientNioEventLoopThreadCount(), equalTo(10));
            assertThat(System.getProperty("mockserver.clientNioEventLoopThreadCount"), equalTo("10"));
            assertThat(configuration.clientNioEventLoopThreadCount(), equalTo(10));

            // when - setter
            configuration.clientNioEventLoopThreadCount(20);

            // then - getter
            assertThat(configuration.clientNioEventLoopThreadCount(), equalTo(20));
        } finally {
            ConfigurationProperties.clientNioEventLoopThreadCount(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxFutureTimeoutInMillis() {
        long original = ConfigurationProperties.maxFutureTimeout();
        try {
            // then - default value
            assertThat(configuration.maxFutureTimeoutInMillis(), equalTo(90000L));

            // when - system property setter
            ConfigurationProperties.maxFutureTimeout(10L);

            // then - system property getter
            assertThat(ConfigurationProperties.maxFutureTimeout(), equalTo(10L));
            assertThat(System.getProperty("mockserver.maxFutureTimeout"), equalTo("10"));
            assertThat(configuration.maxFutureTimeoutInMillis(), equalTo(10L));

            // when - setter
            configuration.maxFutureTimeoutInMillis(20L);

            // then - getter
            assertThat(configuration.maxFutureTimeoutInMillis(), equalTo(20L));
        } finally {
            ConfigurationProperties.maxFutureTimeout(original);
        }
    }

    @Test
    public void shouldSetAndGetMatchersFailFast() {
        boolean original = ConfigurationProperties.matchersFailFast();
        try {
            // then - default value
            assertThat(configuration.matchersFailFast(), equalTo(true));

            // when - system property setter
            ConfigurationProperties.matchersFailFast(false);

            // then - system property getter
            assertThat(ConfigurationProperties.matchersFailFast(), equalTo(false));
            assertThat(System.getProperty("mockserver.matchersFailFast"), equalTo("false"));
            assertThat(configuration.matchersFailFast(), equalTo(false));
            ConfigurationProperties.matchersFailFast(original);

            // when - setter
            configuration.matchersFailFast(false);

            // then - getter
            assertThat(configuration.matchersFailFast(), equalTo(false));
        } finally {
            ConfigurationProperties.matchersFailFast(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxSocketTimeoutInMillis() {
        long original = ConfigurationProperties.maxSocketTimeout();
        try {
            // then - default value
            assertThat(configuration.maxSocketTimeoutInMillis(), equalTo(20000L));

            // when - system property setter
            ConfigurationProperties.maxSocketTimeout(10L);

            // then - system property getter
            assertThat(ConfigurationProperties.maxSocketTimeout(), equalTo(10L));
            assertThat(System.getProperty("mockserver.maxSocketTimeout"), equalTo("10"));
            assertThat(configuration.maxSocketTimeoutInMillis(), equalTo(10L));

            // when - setter
            configuration.maxSocketTimeoutInMillis(20L);

            // then - getter
            assertThat(configuration.maxSocketTimeoutInMillis(), equalTo(20L));
        } finally {
            ConfigurationProperties.maxSocketTimeout(original);
        }
    }

    @Test
    public void shouldSetAndGetSocketConnectionTimeoutInMillis() {
        long original = ConfigurationProperties.socketConnectionTimeout();
        try {
            // then - default value
            assertThat(configuration.socketConnectionTimeoutInMillis(), equalTo(20000L));

            // when - system property setter
            ConfigurationProperties.socketConnectionTimeout(10L);

            // then - system property getter
            assertThat(ConfigurationProperties.socketConnectionTimeout(), equalTo(10L));
            assertThat(System.getProperty("mockserver.socketConnectionTimeout"), equalTo("10"));
            assertThat(configuration.socketConnectionTimeoutInMillis(), equalTo(10L));

            // when - setter
            configuration.socketConnectionTimeoutInMillis(20L);

            // then - getter
            assertThat(configuration.socketConnectionTimeoutInMillis(), equalTo(20L));
        } finally {
            ConfigurationProperties.socketConnectionTimeout(original);
        }
    }

    @Test
    public void shouldSetAndGetAlwaysCloseSocketConnections() {
        boolean original = ConfigurationProperties.alwaysCloseSocketConnections();
        try {
            // then - default value
            assertThat(configuration.alwaysCloseSocketConnections(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.alwaysCloseSocketConnections(true);

            // then - system property getter
            assertThat(ConfigurationProperties.alwaysCloseSocketConnections(), equalTo(true));
            assertThat(System.getProperty("mockserver.alwaysCloseSocketConnections"), equalTo("true"));
            assertThat(configuration.alwaysCloseSocketConnections(), equalTo(true));
            ConfigurationProperties.alwaysCloseSocketConnections(original);

            // when - setter
            configuration.alwaysCloseSocketConnections(true);

            // then - getter
            assertThat(configuration.alwaysCloseSocketConnections(), equalTo(true));
        } finally {
            ConfigurationProperties.alwaysCloseSocketConnections(original);
        }
    }

    @Test
    public void shouldSetAndGetLocalBoundIP() {
        String original = ConfigurationProperties.localBoundIP();
        try {
            // then - default value
            assertThat(configuration.localBoundIP(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.localBoundIP("0.0.0.0");

            // then - system property getter
            assertThat(ConfigurationProperties.localBoundIP(), equalTo("0.0.0.0"));
            assertThat(System.getProperty("mockserver.localBoundIP"), equalTo("0.0.0.0"));
            assertThat(configuration.localBoundIP(), equalTo("0.0.0.0"));
            ConfigurationProperties.localBoundIP(original);

            // when - setter
            configuration.localBoundIP("0.0.0.0");

            // then - getter
            assertThat(configuration.localBoundIP(), equalTo("0.0.0.0"));
        } finally {
            ConfigurationProperties.localBoundIP(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxInitialLineLength() {
        int original = ConfigurationProperties.maxInitialLineLength();
        try {
            // then - default value
            assertThat(configuration.maxInitialLineLength(), equalTo(Integer.MAX_VALUE));

            // when - system property setter
            ConfigurationProperties.maxInitialLineLength(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxInitialLineLength(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxInitialLineLength"), equalTo("10"));
            assertThat(configuration.maxInitialLineLength(), equalTo(10));

            // when - setter
            configuration.maxInitialLineLength(20);

            // then - getter
            assertThat(configuration.maxInitialLineLength(), equalTo(20));
        } finally {
            ConfigurationProperties.maxInitialLineLength(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxHeaderSize() {
        int original = ConfigurationProperties.maxHeaderSize();
        try {
            // then - default value
            assertThat(configuration.maxHeaderSize(), equalTo(Integer.MAX_VALUE));

            // when - system property setter
            ConfigurationProperties.maxHeaderSize(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxHeaderSize(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxHeaderSize"), equalTo("10"));
            assertThat(configuration.maxHeaderSize(), equalTo(10));

            // when - setter
            configuration.maxHeaderSize(20);

            // then - getter
            assertThat(configuration.maxHeaderSize(), equalTo(20));
        } finally {
            ConfigurationProperties.maxHeaderSize(original);
        }
    }

    @Test
    public void shouldSetAndGetMaxChunkSize() {
        int original = ConfigurationProperties.maxChunkSize();
        try {
            // then - default value
            assertThat(configuration.maxChunkSize(), equalTo(Integer.MAX_VALUE));

            // when - system property setter
            ConfigurationProperties.maxChunkSize(10);

            // then - system property getter
            assertThat(ConfigurationProperties.maxChunkSize(), equalTo(10));
            assertThat(System.getProperty("mockserver.maxChunkSize"), equalTo("10"));
            assertThat(configuration.maxChunkSize(), equalTo(10));

            // when - setter
            configuration.maxChunkSize(20);

            // then - getter
            assertThat(configuration.maxChunkSize(), equalTo(20));
        } finally {
            ConfigurationProperties.maxChunkSize(original);
        }
    }

    @Test
    public void shouldSetAndGetUseSemicolonAsQueryParameterSeparator() {
        boolean original = ConfigurationProperties.useSemicolonAsQueryParameterSeparator();
        try {
            // then - default value
            assertThat(configuration.useSemicolonAsQueryParameterSeparator(), equalTo(true));

            // when - system property setter
            ConfigurationProperties.useSemicolonAsQueryParameterSeparator(false);

            // then - system property getter
            assertThat(ConfigurationProperties.useSemicolonAsQueryParameterSeparator(), equalTo(false));
            assertThat(System.getProperty("mockserver.useSemicolonAsQueryParameterSeparator"), equalTo("false"));
            assertThat(configuration.useSemicolonAsQueryParameterSeparator(), equalTo(false));
            ConfigurationProperties.useSemicolonAsQueryParameterSeparator(original);

            // when - setter
            configuration.useSemicolonAsQueryParameterSeparator(false);

            // then - getter
            assertThat(configuration.useSemicolonAsQueryParameterSeparator(), equalTo(false));
        } finally {
            ConfigurationProperties.useSemicolonAsQueryParameterSeparator(original);
        }
    }

    @Test
    public void shouldSetAndGetAssumeAllRequestsAreHttp() {
        boolean original = ConfigurationProperties.assumeAllRequestsAreHttp();
        try {
            // then - default value
            assertThat(configuration.assumeAllRequestsAreHttp(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.assumeAllRequestsAreHttp(true);

            // then - system property getter
            assertThat(ConfigurationProperties.assumeAllRequestsAreHttp(), equalTo(true));
            assertThat(System.getProperty("mockserver.assumeAllRequestsAreHttp"), equalTo("true"));
            assertThat(configuration.assumeAllRequestsAreHttp(), equalTo(true));
            ConfigurationProperties.assumeAllRequestsAreHttp(original);

            // when - setter
            configuration.assumeAllRequestsAreHttp(true);

            // then - getter
            assertThat(configuration.assumeAllRequestsAreHttp(), equalTo(true));
        } finally {
            ConfigurationProperties.assumeAllRequestsAreHttp(original);
        }
    }

    @Test
    public void shouldSetAndGetEnableCORSForAPI() {
        boolean original = ConfigurationProperties.enableCORSForAPI();
        try {
            // then - default value
            assertThat(configuration.enableCORSForAPI(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.enableCORSForAPI(true);

            // then - system property getter
            assertThat(ConfigurationProperties.enableCORSForAPI(), equalTo(true));
            assertThat(System.getProperty("mockserver.enableCORSForAPI"), equalTo("true"));
            assertThat(configuration.enableCORSForAPI(), equalTo(true));
            ConfigurationProperties.enableCORSForAPI(original);

            // when - setter
            configuration.enableCORSForAPI(true);

            // then - getter
            assertThat(configuration.enableCORSForAPI(), equalTo(true));
        } finally {
            ConfigurationProperties.enableCORSForAPI(original);
        }
    }

    @Test
    public void shouldSetAndGetEnableCORSForAllResponses() {
        boolean original = ConfigurationProperties.enableCORSForAllResponses();
        try {
            // then - default value
            assertThat(configuration.enableCORSForAllResponses(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.enableCORSForAllResponses(true);

            // then - system property getter
            assertThat(ConfigurationProperties.enableCORSForAllResponses(), equalTo(true));
            assertThat(System.getProperty("mockserver.enableCORSForAllResponses"), equalTo("true"));
            assertThat(configuration.enableCORSForAllResponses(), equalTo(true));
            ConfigurationProperties.enableCORSForAllResponses(original);

            // when - setter
            configuration.enableCORSForAllResponses(true);

            // then - getter
            assertThat(configuration.enableCORSForAllResponses(), equalTo(true));
        } finally {
            ConfigurationProperties.enableCORSForAllResponses(original);
        }
    }

    @Test
    public void shouldSetAndGetCorsAllowOrigin() {
        String original = ConfigurationProperties.corsAllowOrigin();
        try {
            // then - default value
            assertThat(configuration.corsAllowOrigin(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.corsAllowOrigin("*");

            // then - system property getter
            assertThat(ConfigurationProperties.corsAllowOrigin(), equalTo("*"));
            assertThat(System.getProperty("mockserver.corsAllowOrigin"), equalTo("*"));
            assertThat(configuration.corsAllowOrigin(), equalTo("*"));

            // when - setter
            configuration.corsAllowOrigin("www.mock-server.com");

            // then - getter
            assertThat(configuration.corsAllowOrigin(), equalTo("www.mock-server.com"));
        } finally {
            ConfigurationProperties.corsAllowOrigin(original);
        }
    }

    @Test
    public void shouldSetAndGetCorsAllowMethods() {
        String original = ConfigurationProperties.corsAllowMethods();
        try {
            // then - default value
            assertThat(configuration.corsAllowMethods(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.corsAllowMethods("CONNECT, DELETE");

            // then - system property getter
            assertThat(ConfigurationProperties.corsAllowMethods(), equalTo("CONNECT, DELETE"));
            assertThat(System.getProperty("mockserver.corsAllowMethods"), equalTo("CONNECT, DELETE"));
            assertThat(configuration.corsAllowMethods(), equalTo("CONNECT, DELETE"));

            // when - setter
            configuration.corsAllowMethods("CONNECT, DELETE, GET");

            // then - getter
            assertThat(configuration.corsAllowMethods(), equalTo("CONNECT, DELETE, GET"));
        } finally {
            ConfigurationProperties.corsAllowMethods(original);
        }
    }

    @Test
    public void shouldSetAndGetCorsAllowHeaders() {
        String original = ConfigurationProperties.corsAllowHeaders();
        try {
            // then - default value
            assertThat(configuration.corsAllowHeaders(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.corsAllowHeaders("Allow, Content-Encoding");

            // then - system property getter
            assertThat(ConfigurationProperties.corsAllowHeaders(), equalTo("Allow, Content-Encoding"));
            assertThat(System.getProperty("mockserver.corsAllowHeaders"), equalTo("Allow, Content-Encoding"));
            assertThat(configuration.corsAllowHeaders(), equalTo("Allow, Content-Encoding"));

            // when - setter
            configuration.corsAllowHeaders("Allow, Content-Encoding, Content-Length");

            // then - getter
            assertThat(configuration.corsAllowHeaders(), equalTo("Allow, Content-Encoding, Content-Length"));
        } finally {
            ConfigurationProperties.corsAllowHeaders(original);
        }
    }

    @Test
    public void shouldSetAndGetCorsAllowCredentials() {
        boolean original = ConfigurationProperties.corsAllowCredentials();
        try {
            // then - default value
            assertThat(configuration.corsAllowCredentials(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.corsAllowCredentials(true);

            // then - system property getter
            assertThat(ConfigurationProperties.corsAllowCredentials(), equalTo(true));
            assertThat(System.getProperty("mockserver.corsAllowCredentials"), equalTo("true"));
            assertThat(configuration.corsAllowCredentials(), equalTo(true));
            ConfigurationProperties.corsAllowCredentials(original);

            // when - setter
            configuration.corsAllowCredentials(true);

            // then - getter
            assertThat(configuration.corsAllowCredentials(), equalTo(true));
        } finally {
            ConfigurationProperties.corsAllowCredentials(original);
        }
    }

    @Test
    public void shouldSetAndGetCorsMaxAgeInSeconds() {
        int original = ConfigurationProperties.corsMaxAgeInSeconds();
        try {
            // then - default value
            assertThat(configuration.corsMaxAgeInSeconds(), equalTo(0));

            // when - system property setter
            ConfigurationProperties.corsMaxAgeInSeconds(10);

            // then - system property getter
            assertThat(ConfigurationProperties.corsMaxAgeInSeconds(), equalTo(10));
            assertThat(System.getProperty("mockserver.corsMaxAgeInSeconds"), equalTo("10"));
            assertThat(configuration.corsMaxAgeInSeconds(), equalTo(10));

            // when - setter
            configuration.corsMaxAgeInSeconds(20);

            // then - getter
            assertThat(configuration.corsMaxAgeInSeconds(), equalTo(20));
        } finally {
            ConfigurationProperties.corsMaxAgeInSeconds(original);
        }
    }

    @Test
    public void shouldSetAndGetJavaScriptDisallowedClasses() {
        String original = ConfigurationProperties.javascriptDisallowedClasses();
        try {
            // then - default value
            assertThat(configuration.javascriptDisallowedClasses(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.javascriptDisallowedClasses("java.lang.Runtime");

            // then - system property getter
            assertThat(ConfigurationProperties.javascriptDisallowedClasses(), equalTo("java.lang.Runtime"));
            assertThat(System.getProperty("mockserver.javascriptDisallowedClasses"), equalTo("java.lang.Runtime"));
            assertThat(configuration.javascriptDisallowedClasses(), equalTo("java.lang.Runtime"));

            // when - setter
            configuration.javascriptDisallowedClasses("java.lang.Class");

            // then - getter
            assertThat(configuration.javascriptDisallowedClasses(), equalTo("java.lang.Class"));
        } finally {
            ConfigurationProperties.javascriptDisallowedClasses(original);
        }
    }

    @Test
    public void shouldSetAndGetJavaScriptDisallowedText() {
        String original = ConfigurationProperties.javascriptDisallowedText();
        try {
            // then - default value
            assertThat(configuration.javascriptDisallowedText(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.javascriptDisallowedText("some_text");

            // then - system property getter
            assertThat(ConfigurationProperties.javascriptDisallowedText(), equalTo("some_text"));
            assertThat(System.getProperty("mockserver.javascriptDisallowedText"), equalTo("some_text"));
            assertThat(configuration.javascriptDisallowedText(), equalTo("some_text"));

            // when - setter
            configuration.javascriptDisallowedText("some_other_text");

            // then - getter
            assertThat(configuration.javascriptDisallowedText(), equalTo("some_other_text"));
        } finally {
            ConfigurationProperties.javascriptDisallowedText(original);
        }
    }

    @Test
    public void shouldSetAndGetVelocityDisallowClassLoading() {
        boolean original = ConfigurationProperties.velocityDisallowClassLoading();
        try {
            // then - default value
            assertThat(configuration.velocityDisallowClassLoading(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.velocityDisallowClassLoading(true);

            // then - system property getter
            assertThat(ConfigurationProperties.velocityDisallowClassLoading(), equalTo(true));
            assertThat(System.getProperty("mockserver.velocityDisallowClassLoading"), equalTo("true"));
            assertThat(configuration.velocityDisallowClassLoading(), equalTo(true));

            // when - setter
            configuration.velocityDisallowClassLoading(false);

            // then - getter
            assertThat(configuration.velocityDisallowClassLoading(), equalTo(false));
        } finally {
            ConfigurationProperties.velocityDisallowClassLoading(original);
        }
    }

    @Test
    public void shouldSetAndGetVelocityDisallowedText() {
        String original = ConfigurationProperties.velocityDisallowedText();
        try {
            // then - default value
            assertThat(configuration.velocityDisallowedText(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.velocityDisallowedText("some_text");

            // then - system property getter
            assertThat(ConfigurationProperties.velocityDisallowedText(), equalTo("some_text"));
            assertThat(System.getProperty("mockserver.velocityDisallowedText"), equalTo("some_text"));
            assertThat(configuration.velocityDisallowedText(), equalTo("some_text"));

            // when - setter
            configuration.velocityDisallowedText("some_other_text");

            // then - getter
            assertThat(configuration.velocityDisallowedText(), equalTo("some_other_text"));
        } finally {
            ConfigurationProperties.velocityDisallowedText(original);
        }
    }

    @Test
    public void shouldSetAndGetInitializationClass() {
        String original = ConfigurationProperties.initializationClass();
        try {
            // then - default value
            assertThat(configuration.initializationClass(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.initializationClass(ExpectationInitializerExample.class.getName());

            // then - system property getter
            assertThat(ConfigurationProperties.initializationClass(), equalTo(ExpectationInitializerExample.class.getName()));
            assertThat(System.getProperty("mockserver.initializationClass"), equalTo(ExpectationInitializerExample.class.getName()));
            assertThat(configuration.initializationClass(), equalTo(ExpectationInitializerExample.class.getName()));
            ConfigurationProperties.initializationClass(original);

            // when - setter
            configuration.initializationClass(ExpectationInitializerExample.class.getName());

            // then - getter
            assertThat(configuration.initializationClass(), equalTo(ExpectationInitializerExample.class.getName()));
        } finally {
            ConfigurationProperties.initializationClass(original);
        }
    }

    @Test
    public void shouldSetAndGetInitializationJsonPath() {
        String original = ConfigurationProperties.initializationJsonPath();
        try {
            // then - default value
            assertThat(configuration.initializationJsonPath(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.initializationJsonPath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.initializationJsonPath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.initializationJsonPath"), equalTo(firstPath));
            assertThat(configuration.initializationJsonPath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.initializationJsonPath(secondPath);

            // then - getter
            assertThat(configuration.initializationJsonPath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.initializationJsonPath(original);
        }
    }

    @Test
    public void shouldSetAndGetWatchInitializationJson() {
        boolean original = ConfigurationProperties.watchInitializationJson();
        try {
            // then - default value
            assertThat(configuration.watchInitializationJson(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.watchInitializationJson(true);

            // then - system property getter
            assertThat(ConfigurationProperties.watchInitializationJson(), equalTo(true));
            assertThat(System.getProperty("mockserver.watchInitializationJson"), equalTo("true"));
            assertThat(configuration.watchInitializationJson(), equalTo(true));
            ConfigurationProperties.watchInitializationJson(original);

            // when - setter
            configuration.watchInitializationJson(true);

            // then - getter
            assertThat(configuration.watchInitializationJson(), equalTo(true));
        } finally {
            ConfigurationProperties.watchInitializationJson(original);
        }
    }

    @Test
    public void shouldSetAndGetPersistExpectations() {
        boolean original = ConfigurationProperties.persistExpectations();
        try {
            // then - default value
            assertThat(configuration.persistExpectations(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.persistExpectations(true);

            // then - system property getter
            assertThat(ConfigurationProperties.persistExpectations(), equalTo(true));
            assertThat(System.getProperty("mockserver.persistExpectations"), equalTo("true"));
            assertThat(configuration.persistExpectations(), equalTo(true));
            ConfigurationProperties.persistExpectations(original);

            // when - setter
            configuration.persistExpectations(true);

            // then - getter
            assertThat(configuration.persistExpectations(), equalTo(true));
        } finally {
            ConfigurationProperties.persistExpectations(original);
        }
    }

    @Test
    public void shouldSetAndGetPersistedExpectationsPath() {
        String original = ConfigurationProperties.persistedExpectationsPath();
        try {
            // then - default value
            assertThat(configuration.persistedExpectationsPath(), equalTo("persistedExpectations.json"));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.persistedExpectationsPath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.persistedExpectationsPath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.persistedExpectationsPath"), equalTo(firstPath));
            assertThat(configuration.persistedExpectationsPath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.persistedExpectationsPath(secondPath);

            // then - getter
            assertThat(configuration.persistedExpectationsPath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(original);
        }
    }

    @Test
    public void shouldSetAndGetMaximumNumberOfRequestToReturnInVerificationFailure() {
        int original = ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure();
        try {
            // then - default value
            assertThat(configuration.maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(10));

            // when - system property setter
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(5);

            // then - system property getter
            assertThat(ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(5));
            assertThat(System.getProperty("mockserver.maximumNumberOfRequestToReturnInVerificationFailure"), equalTo("5"));
            assertThat(configuration.maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(5));

            // when - setter
            String secondPath = tempFilePath();
            configuration.maximumNumberOfRequestToReturnInVerificationFailure(20);

            // then - getter
            assertThat(configuration.maximumNumberOfRequestToReturnInVerificationFailure(), equalTo(20));
        } finally {
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(original);
        }
    }

    @Test
    public void shouldSetAndGetAttemptToProxyIfNoMatchingExpectation() {
        boolean original = ConfigurationProperties.attemptToProxyIfNoMatchingExpectation();
        try {
            // then - default value
            assertThat(configuration.attemptToProxyIfNoMatchingExpectation(), equalTo(true));

            // when - system property setter
            ConfigurationProperties.attemptToProxyIfNoMatchingExpectation(false);

            // then - system property getter
            assertThat(ConfigurationProperties.attemptToProxyIfNoMatchingExpectation(), equalTo(false));
            assertThat(System.getProperty("mockserver.attemptToProxyIfNoMatchingExpectation"), equalTo("false"));
            assertThat(configuration.attemptToProxyIfNoMatchingExpectation(), equalTo(false));
            ConfigurationProperties.attemptToProxyIfNoMatchingExpectation(original);

            // when - setter
            configuration.attemptToProxyIfNoMatchingExpectation(false);

            // then - getter
            assertThat(configuration.attemptToProxyIfNoMatchingExpectation(), equalTo(false));
        } finally {
            ConfigurationProperties.attemptToProxyIfNoMatchingExpectation(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardHttpProxy() {
        InetSocketAddress original = ConfigurationProperties.forwardHttpProxy();
        try {
            // then - default value
            assertThat(configuration.forwardHttpProxy(), equalTo(null));

            // when - system property setter
            ConfigurationProperties.forwardHttpProxy("127.0.0.1:1080");

            // then - system property getter
            assertThat(ConfigurationProperties.forwardHttpProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));
            assertThat(System.getProperty("mockserver.forwardHttpProxy"), equalTo("127.0.0.1:1080"));
            assertThat(configuration.forwardHttpProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));

            // when - setter
            configuration.forwardHttpProxy(new InetSocketAddress("127.0.0.1", 1090));

            // then - getter
            assertThat(configuration.forwardHttpProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1090)));
        } finally {
            ConfigurationProperties.forwardHttpProxy(original != null ? original.toString() : null);
        }
    }

    @Test
    public void shouldSetAndGetForwardHttpsProxy() {
        InetSocketAddress original = ConfigurationProperties.forwardHttpsProxy();
        try {
            // then - default value
            assertThat(configuration.forwardHttpsProxy(), equalTo(null));

            // when - system property setter
            ConfigurationProperties.forwardHttpsProxy("127.0.0.1:1080");

            // then - system property getter
            assertThat(ConfigurationProperties.forwardHttpsProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));
            assertThat(System.getProperty("mockserver.forwardHttpsProxy"), equalTo("127.0.0.1:1080"));
            assertThat(configuration.forwardHttpsProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));

            // when - setter
            configuration.forwardHttpsProxy(new InetSocketAddress("127.0.0.1", 1090));

            // then - getter
            assertThat(configuration.forwardHttpsProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1090)));
        } finally {
            ConfigurationProperties.forwardHttpsProxy(original != null ? original.toString() : null);
        }
    }

    @Test
    public void shouldSetAndGetForwardSocksProxy() {
        InetSocketAddress original = ConfigurationProperties.forwardSocksProxy();
        try {
            // then - default value
            assertThat(configuration.forwardSocksProxy(), equalTo(null));

            // when - system property setter
            ConfigurationProperties.forwardSocksProxy("127.0.0.1:1080");

            // then - system property getter
            assertThat(ConfigurationProperties.forwardSocksProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));
            assertThat(System.getProperty("mockserver.forwardSocksProxy"), equalTo("127.0.0.1:1080"));
            assertThat(configuration.forwardSocksProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1080)));

            // when - setter
            configuration.forwardSocksProxy(new InetSocketAddress("127.0.0.1", 1090));

            // then - getter
            assertThat(configuration.forwardSocksProxy(), equalTo(new InetSocketAddress("127.0.0.1", 1090)));
        } finally {
            ConfigurationProperties.forwardSocksProxy(original != null ? original.toString() : null);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyAuthenticationUsername() {
        String original = ConfigurationProperties.forwardProxyAuthenticationUsername();
        try {
            // then - default value
            assertThat(configuration.forwardProxyAuthenticationUsername(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.forwardProxyAuthenticationUsername("john.doe");

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyAuthenticationUsername(), equalTo("john.doe"));
            assertThat(System.getProperty("mockserver.forwardProxyAuthenticationUsername"), equalTo("john.doe"));
            assertThat(configuration.forwardProxyAuthenticationUsername(), equalTo("john.doe"));

            // when - setter
            configuration.forwardProxyAuthenticationUsername("fred.smith");

            // then - getter
            assertThat(configuration.forwardProxyAuthenticationUsername(), equalTo("fred.smith"));
        } finally {
            ConfigurationProperties.forwardProxyAuthenticationUsername(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyAuthenticationPassword() {
        String original = ConfigurationProperties.forwardProxyAuthenticationPassword();
        try {
            // then - default value
            assertThat(configuration.forwardProxyAuthenticationPassword(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.forwardProxyAuthenticationPassword("pa55w0rd");

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyAuthenticationPassword(), equalTo("pa55w0rd"));
            assertThat(System.getProperty("mockserver.forwardProxyAuthenticationPassword"), equalTo("pa55w0rd"));
            assertThat(configuration.forwardProxyAuthenticationPassword(), equalTo("pa55w0rd"));

            // when - setter
            configuration.forwardProxyAuthenticationPassword("w0rdpa55");

            // then - getter
            assertThat(configuration.forwardProxyAuthenticationPassword(), equalTo("w0rdpa55"));
        } finally {
            ConfigurationProperties.forwardProxyAuthenticationPassword(original);
        }
    }

    @Test
    public void shouldSetAndGetProxyAuthenticationRealm() {
        String original = ConfigurationProperties.proxyAuthenticationRealm();
        try {
            // then - default value
            assertThat(configuration.proxyAuthenticationRealm(), equalTo("MockServer HTTP Proxy"));

            // when - system property setter
            ConfigurationProperties.proxyAuthenticationRealm("Some Realm");

            // then - system property getter
            assertThat(ConfigurationProperties.proxyAuthenticationRealm(), equalTo("Some Realm"));
            assertThat(System.getProperty("mockserver.proxyAuthenticationRealm"), equalTo("Some Realm"));
            assertThat(configuration.proxyAuthenticationRealm(), equalTo("Some Realm"));

            // when - setter
            configuration.proxyAuthenticationRealm("Some Other Realm");

            // then - getter
            assertThat(configuration.proxyAuthenticationRealm(), equalTo("Some Other Realm"));
        } finally {
            ConfigurationProperties.proxyAuthenticationRealm(original);
        }
    }

    @Test
    public void shouldSetAndGetProxyAuthenticationUsername() {
        String original = ConfigurationProperties.proxyAuthenticationUsername();
        try {
            // then - default value
            assertThat(configuration.proxyAuthenticationUsername(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.proxyAuthenticationUsername("john.doe");

            // then - system property getter
            assertThat(ConfigurationProperties.proxyAuthenticationUsername(), equalTo("john.doe"));
            assertThat(System.getProperty("mockserver.proxyAuthenticationUsername"), equalTo("john.doe"));
            assertThat(configuration.proxyAuthenticationUsername(), equalTo("john.doe"));

            // when - setter
            configuration.proxyAuthenticationUsername("fred.smith");

            // then - getter
            assertThat(configuration.proxyAuthenticationUsername(), equalTo("fred.smith"));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(original);
        }
    }

    @Test
    public void shouldSetAndGetProxyAuthenticationPassword() {
        String original = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // then - default value
            assertThat(configuration.proxyAuthenticationPassword(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.proxyAuthenticationPassword("pa55w0rd");

            // then - system property getter
            assertThat(ConfigurationProperties.proxyAuthenticationPassword(), equalTo("pa55w0rd"));
            assertThat(System.getProperty("mockserver.proxyAuthenticationPassword"), equalTo("pa55w0rd"));
            assertThat(configuration.proxyAuthenticationPassword(), equalTo("pa55w0rd"));

            // when - setter
            configuration.proxyAuthenticationPassword("w0rdpa55");

            // then - getter
            assertThat(configuration.proxyAuthenticationPassword(), equalTo("w0rdpa55"));
        } finally {
            ConfigurationProperties.proxyAuthenticationPassword(original);
        }
    }

    @Test
    public void shouldSetAndGetLivenessHttpGetPath() {
        String original = ConfigurationProperties.livenessHttpGetPath();
        try {
            // then - default value
            assertThat(configuration.livenessHttpGetPath(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.livenessHttpGetPath("/liveness");

            // then - system property getter
            assertThat(ConfigurationProperties.livenessHttpGetPath(), equalTo("/liveness"));
            assertThat(System.getProperty("mockserver.livenessHttpGetPath"), equalTo("/liveness"));
            assertThat(configuration.livenessHttpGetPath(), equalTo("/liveness"));

            // when - setter
            configuration.livenessHttpGetPath("/livenessProbe");

            // then - getter
            assertThat(configuration.livenessHttpGetPath(), equalTo("/livenessProbe"));
        } finally {
            ConfigurationProperties.livenessHttpGetPath(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneTLSMutualAuthenticationRequired() {
        boolean original = ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired();
        try {
            // then - default value
            assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(true);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(), equalTo(true));
            assertThat(System.getProperty("mockserver.controlPlaneTLSMutualAuthenticationRequired"), equalTo("true"));
            assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), equalTo(true));
            ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(original);

            // when - setter
            configuration.controlPlaneTLSMutualAuthenticationRequired(true);

            // then - getter
            assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), equalTo(true));
        } finally {
            ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneTLSMutualAuthenticationCAChain() {
        String original = ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain();
        try {
            // then - default value
            assertThat(configuration.controlPlaneTLSMutualAuthenticationCAChain(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.controlPlaneTLSMutualAuthenticationCAChain"), equalTo(firstPath));
            assertThat(configuration.controlPlaneTLSMutualAuthenticationCAChain(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.controlPlaneTLSMutualAuthenticationCAChain(secondPath);

            // then - getter
            assertThat(configuration.controlPlaneTLSMutualAuthenticationCAChain(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlanePrivateKeyPath() {
        String original = ConfigurationProperties.controlPlanePrivateKeyPath();
        try {
            // then - default value
            assertThat(configuration.controlPlanePrivateKeyPath(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.controlPlanePrivateKeyPath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlanePrivateKeyPath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.controlPlanePrivateKeyPath"), equalTo(firstPath));
            assertThat(configuration.controlPlanePrivateKeyPath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.controlPlanePrivateKeyPath(secondPath);

            // then - getter
            assertThat(configuration.controlPlanePrivateKeyPath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.controlPlanePrivateKeyPath(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneX509CertificatePath() {
        String original = ConfigurationProperties.controlPlaneX509CertificatePath();
        try {
            // then - default value
            assertThat(configuration.controlPlaneX509CertificatePath(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.controlPlaneX509CertificatePath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneX509CertificatePath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.controlPlaneX509CertificatePath"), equalTo(firstPath));
            assertThat(configuration.controlPlaneX509CertificatePath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.controlPlaneX509CertificatePath(secondPath);

            // then - getter
            assertThat(configuration.controlPlaneX509CertificatePath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.controlPlaneX509CertificatePath(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneJWTAuthenticationRequired() {
        boolean original = ConfigurationProperties.controlPlaneJWTAuthenticationRequired();
        try {
            // then - default value
            assertThat(configuration.controlPlaneJWTAuthenticationRequired(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.controlPlaneJWTAuthenticationRequired(true);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneJWTAuthenticationRequired(), equalTo(true));
            assertThat(System.getProperty("mockserver.controlPlaneJWTAuthenticationRequired"), equalTo("true"));
            assertThat(configuration.controlPlaneJWTAuthenticationRequired(), equalTo(true));
            ConfigurationProperties.controlPlaneJWTAuthenticationRequired(original);

            // when - setter
            configuration.controlPlaneJWTAuthenticationRequired(true);

            // then - getter
            assertThat(configuration.controlPlaneJWTAuthenticationRequired(), equalTo(true));
        } finally {
            ConfigurationProperties.controlPlaneJWTAuthenticationRequired(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneJWTAuthenticationJWKSource() {
        String original = ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource();
        try {
            // then - default value
            assertThat(configuration.controlPlaneJWTAuthenticationJWKSource(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.controlPlaneJWTAuthenticationJWKSource"), equalTo(firstPath));
            assertThat(configuration.controlPlaneJWTAuthenticationJWKSource(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.controlPlaneJWTAuthenticationJWKSource(secondPath);

            // then - getter
            assertThat(configuration.controlPlaneJWTAuthenticationJWKSource(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneJWTAuthenticationExpectedAudience() {
        String original = ConfigurationProperties.controlPlaneJWTAuthenticationExpectedAudience();
        try {
            // then - default value
            assertThat(configuration.controlPlaneJWTAuthenticationExpectedAudience(), equalTo(""));

            // when - system property setter
            ConfigurationProperties.controlPlaneJWTAuthenticationExpectedAudience("https://mock-server.com");

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneJWTAuthenticationExpectedAudience(), equalTo("https://mock-server.com"));
            assertThat(System.getProperty("mockserver.controlPlaneJWTAuthenticationExpectedAudience"), equalTo("https://mock-server.com"));
            assertThat(configuration.controlPlaneJWTAuthenticationExpectedAudience(), equalTo("https://mock-server.com"));

            // when - setter
            configuration.controlPlaneJWTAuthenticationExpectedAudience("https://google.com");

            // then - getter
            assertThat(configuration.controlPlaneJWTAuthenticationExpectedAudience(), equalTo("https://google.com"));
        } finally {
            ConfigurationProperties.controlPlaneJWTAuthenticationExpectedAudience(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneJWTAuthenticationMatchingClaims() {
        Map<String, String> original = ConfigurationProperties.controlPlaneJWTAuthenticationMatchingClaims();
        try {
            // then - default value
            assertThat(configuration.controlPlaneJWTAuthenticationMatchingClaims(), equalTo(ImmutableMap.of()));

            // when - system property setter
            ConfigurationProperties.controlPlaneJWTAuthenticationMatchingClaims(ImmutableMap.of("sub", "john.doe", "scopes", "basic admin"));

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneJWTAuthenticationMatchingClaims(), equalTo(ImmutableMap.of("sub", "john.doe", "scopes", "basic admin")));
            assertThat(System.getProperty("mockserver.controlPlaneJWTAuthenticationMatchingClaims"), equalTo("sub=john.doe,scopes=basic admin"));
            assertThat(configuration.controlPlaneJWTAuthenticationMatchingClaims(), equalTo(ImmutableMap.of("sub", "john.doe", "scopes", "basic admin")));

            // when - setter
            configuration.controlPlaneJWTAuthenticationMatchingClaims(ImmutableMap.of("sub", "fred.smith"));

            // then - getter
            assertThat(configuration.controlPlaneJWTAuthenticationMatchingClaims(), equalTo(ImmutableMap.of("sub", "fred.smith")));
        } finally {
            ConfigurationProperties.controlPlaneJWTAuthenticationMatchingClaims(original);
        }
    }

    @Test
    public void shouldSetAndGetControlPlaneJWTAuthenticationRequiredClaims() {
        Set<String> original = ConfigurationProperties.controlPlaneJWTAuthenticationRequiredClaims();
        try {
            // then - default value
            assertThat(configuration.controlPlaneJWTAuthenticationRequiredClaims(), equalTo(ImmutableSet.of()));

            // when - system property setter
            ConfigurationProperties.controlPlaneJWTAuthenticationRequiredClaims(ImmutableSet.of("sub", "scopes"));

            // then - system property getter
            assertThat(ConfigurationProperties.controlPlaneJWTAuthenticationRequiredClaims(), equalTo(ImmutableSet.of("sub", "scopes")));
            assertThat(System.getProperty("mockserver.controlPlaneJWTAuthenticationRequiredClaims"), equalTo("sub,scopes"));
            assertThat(configuration.controlPlaneJWTAuthenticationRequiredClaims(), equalTo(ImmutableSet.of("sub", "scopes")));

            // when - setter
            configuration.controlPlaneJWTAuthenticationRequiredClaims(ImmutableSet.of("scopes"));

            // then - getter
            assertThat(configuration.controlPlaneJWTAuthenticationRequiredClaims(), equalTo(ImmutableSet.of("scopes")));
        } finally {
            ConfigurationProperties.controlPlaneJWTAuthenticationRequiredClaims(original);
        }
    }

    @Test
    public void shouldSetAndGetProactivelyInitialiseTLS() {
        boolean original = ConfigurationProperties.proactivelyInitialiseTLS();
        try {
            // then - default value
            assertThat(configuration.proactivelyInitialiseTLS(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.proactivelyInitialiseTLS(true);

            // then - system property getter
            assertThat(ConfigurationProperties.proactivelyInitialiseTLS(), equalTo(true));
            assertThat(System.getProperty("mockserver.proactivelyInitialiseTLS"), equalTo("true"));
            assertThat(configuration.proactivelyInitialiseTLS(), equalTo(true));
            ConfigurationProperties.proactivelyInitialiseTLS(original);

            // when - setter
            configuration.proactivelyInitialiseTLS(true);

            // then - getter
            assertThat(configuration.proactivelyInitialiseTLS(), equalTo(true));
        } finally {
            ConfigurationProperties.proactivelyInitialiseTLS(original);
        }
    }

    @Test
    public void shouldSetAndGetRebuildTLSContext() {
        // then - default value
        assertThat(configuration.rebuildTLSContext(), equalTo(false));

        // when - setter
        configuration.rebuildTLSContext(true);

        // then - getter
        assertThat(configuration.rebuildTLSContext(), equalTo(true));
    }

    @Test
    public void shouldSetAndGetRebuildServerTLSContext() {
        // then - default value
        assertThat(configuration.rebuildServerTLSContext(), equalTo(false));

        // when - setter
        configuration.rebuildServerTLSContext(true);

        // then - getter
        assertThat(configuration.rebuildServerTLSContext(), equalTo(true));
    }

    @Test
    public void shouldSetAndGetDynamicallyCreateCertificateAuthorityCertificate() {
        boolean original = ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate();
        try {
            // then - default value
            assertThat(configuration.dynamicallyCreateCertificateAuthorityCertificate(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(true);

            // then - system property getter
            assertThat(ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(), equalTo(true));
            assertThat(System.getProperty("mockserver.dynamicallyCreateCertificateAuthorityCertificate"), equalTo("true"));
            assertThat(configuration.dynamicallyCreateCertificateAuthorityCertificate(), equalTo(true));
            ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(original);

            // when - setter
            configuration.dynamicallyCreateCertificateAuthorityCertificate(true);

            // then - getter
            assertThat(configuration.dynamicallyCreateCertificateAuthorityCertificate(), equalTo(true));
        } finally {
            ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(original);
        }
    }

    @Test
    public void shouldSetAndGetDirectoryToSaveDynamicSSLCertificate() {
        String original = ConfigurationProperties.directoryToSaveDynamicSSLCertificate();
        try {
            // then - default value
            assertThat(configuration.directoryToSaveDynamicSSLCertificate(), equalTo("."));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.directoryToSaveDynamicSSLCertificate(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.directoryToSaveDynamicSSLCertificate(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.directoryToSaveDynamicSSLCertificate"), equalTo(firstPath));
            assertThat(configuration.directoryToSaveDynamicSSLCertificate(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.directoryToSaveDynamicSSLCertificate(secondPath);

            // then - getter
            assertThat(configuration.directoryToSaveDynamicSSLCertificate(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.directoryToSaveDynamicSSLCertificate(original);
        }
    }

    @Test
    public void shouldSetAndGetPreventCertificateDynamicUpdate() {
        boolean original = ConfigurationProperties.preventCertificateDynamicUpdate();
        try {
            // then - default value
            assertThat(configuration.preventCertificateDynamicUpdate(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.preventCertificateDynamicUpdate(true);

            // then - system property getter
            assertThat(ConfigurationProperties.preventCertificateDynamicUpdate(), equalTo(true));
            assertThat(System.getProperty("mockserver.preventCertificateDynamicUpdate"), equalTo("true"));
            assertThat(configuration.preventCertificateDynamicUpdate(), equalTo(true));
            ConfigurationProperties.preventCertificateDynamicUpdate(original);

            // when - setter
            configuration.preventCertificateDynamicUpdate(true);

            // then - getter
            assertThat(configuration.preventCertificateDynamicUpdate(), equalTo(true));
        } finally {
            ConfigurationProperties.preventCertificateDynamicUpdate(original);
        }
    }

    @Test
    public void shouldSetAndGetSslCertificateDomainName() {
        String original = ConfigurationProperties.sslCertificateDomainName();
        try {
            // then - default value
            assertThat(configuration.sslCertificateDomainName(), equalTo(KeyAndCertificateFactory.CERTIFICATE_DOMAIN));

            // when - system property setter
            ConfigurationProperties.sslCertificateDomainName("mock-server.co.uk");

            // then - system property getter
            assertThat(ConfigurationProperties.sslCertificateDomainName(), equalTo("mock-server.co.uk"));
            assertThat(System.getProperty("mockserver.sslCertificateDomainName"), equalTo("mock-server.co.uk"));
            assertThat(configuration.sslCertificateDomainName(), equalTo("mock-server.co.uk"));

            // when - setter
            configuration.sslCertificateDomainName("mock-server.org");

            // then - getter
            assertThat(configuration.sslCertificateDomainName(), equalTo("mock-server.org"));
        } finally {
            ConfigurationProperties.sslCertificateDomainName(original);
        }
    }

    @Test
    public void shouldSetAndGetSslSubjectAlternativeNameDomains() {
        Set<String> original = ConfigurationProperties.sslSubjectAlternativeNameDomains();
        try {
            // then - default value
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("localhost")));

            // when - system property setter
            ConfigurationProperties.sslSubjectAlternativeNameDomains(ImmutableSet.of("mock-server.co.uk"));

            // then - system property getter
            assertThat(ConfigurationProperties.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("mock-server.co.uk")));
            assertThat(System.getProperty("mockserver.sslSubjectAlternativeNameDomains"), equalTo("mock-server.co.uk"));
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("mock-server.co.uk")));

            // when - setter
            configuration.sslSubjectAlternativeNameDomains(ImmutableSet.of("mock-server.org"));

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("mock-server.org")));
        } finally {
            ConfigurationProperties.sslSubjectAlternativeNameDomains(original);
        }
    }

    @Test
    public void shouldSetAndGetSslSubjectAlternativeNameIps() {
        Set<String> original = ConfigurationProperties.sslSubjectAlternativeNameIps();
        try {
            // then - default value
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("127.0.0.1", "0.0.0.0")));

            // when - system property setter
            ConfigurationProperties.sslSubjectAlternativeNameIps(ImmutableSet.of("1.2.3.4", "5.6.7.8"));

            // then - system property getter
            assertThat(ConfigurationProperties.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("1.2.3.4", "5.6.7.8")));
            assertThat(System.getProperty("mockserver.sslSubjectAlternativeNameIps"), equalTo("1.2.3.4,5.6.7.8"));
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("1.2.3.4", "5.6.7.8")));

            // when - setter
            configuration.sslSubjectAlternativeNameIps(ImmutableSet.of("10.20.30.40", "50.60.70.80"));

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("10.20.30.40", "50.60.70.80")));
        } finally {
            ConfigurationProperties.sslSubjectAlternativeNameIps(original);
        }
    }

    @Test
    public void shouldSetAndGetCertificateAuthorityPrivateKey() {
        String original = ConfigurationProperties.certificateAuthorityPrivateKey();
        try {
            // then - default value
            assertThat(configuration.certificateAuthorityPrivateKey(), equalTo(ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.certificateAuthorityPrivateKey(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.certificateAuthorityPrivateKey(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.certificateAuthorityPrivateKey"), equalTo(firstPath));
            assertThat(configuration.certificateAuthorityPrivateKey(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.certificateAuthorityPrivateKey(secondPath);

            // then - getter
            assertThat(configuration.certificateAuthorityPrivateKey(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.certificateAuthorityPrivateKey(original);
        }
    }

    @Test
    public void shouldSetAndGetCertificateAuthorityCertificate() {
        String original = ConfigurationProperties.certificateAuthorityCertificate();
        try {
            // then - default value
            assertThat(configuration.certificateAuthorityCertificate(), equalTo(ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.certificateAuthorityCertificate(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.certificateAuthorityCertificate(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.certificateAuthorityCertificate"), equalTo(firstPath));
            assertThat(configuration.certificateAuthorityCertificate(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.certificateAuthorityCertificate(secondPath);

            // then - getter
            assertThat(configuration.certificateAuthorityCertificate(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.certificateAuthorityCertificate(original);
        }
    }

    @Test
    public void shouldSetAndGetPrivateKeyPath() {
        String original = ConfigurationProperties.privateKeyPath();
        try {
            // then - default value
            assertThat(configuration.privateKeyPath(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.privateKeyPath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.privateKeyPath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.privateKeyPath"), equalTo(firstPath));
            assertThat(configuration.privateKeyPath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.privateKeyPath(secondPath);

            // then - getter
            assertThat(configuration.privateKeyPath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.privateKeyPath(original);
        }
    }

    @Test
    public void shouldSetAndGetX509CertificatePath() {
        String original = ConfigurationProperties.x509CertificatePath();
        try {
            // then - default value
            assertThat(configuration.x509CertificatePath(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.x509CertificatePath(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.x509CertificatePath(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.x509CertificatePath"), equalTo(firstPath));
            assertThat(configuration.x509CertificatePath(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.x509CertificatePath(secondPath);

            // then - getter
            assertThat(configuration.x509CertificatePath(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.x509CertificatePath(original);
        }
    }

    @Test
    public void shouldSetAndGetTlsMutualAuthenticationRequired() {
        boolean original = ConfigurationProperties.tlsMutualAuthenticationRequired();
        try {
            // then - default value
            assertThat(configuration.tlsMutualAuthenticationRequired(), equalTo(false));

            // when - system property setter
            ConfigurationProperties.tlsMutualAuthenticationRequired(true);

            // then - system property getter
            assertThat(ConfigurationProperties.tlsMutualAuthenticationRequired(), equalTo(true));
            assertThat(System.getProperty("mockserver.tlsMutualAuthenticationRequired"), equalTo("true"));
            assertThat(configuration.tlsMutualAuthenticationRequired(), equalTo(true));
            ConfigurationProperties.tlsMutualAuthenticationRequired(original);

            // when - setter
            configuration.tlsMutualAuthenticationRequired(true);

            // then - getter
            assertThat(configuration.tlsMutualAuthenticationRequired(), equalTo(true));
        } finally {
            ConfigurationProperties.tlsMutualAuthenticationRequired(original);
        }
    }

    @Test
    public void shouldSetAndGetTlsMutualAuthenticationCertificateChain() {
        String original = ConfigurationProperties.tlsMutualAuthenticationCertificateChain();
        try {
            // then - default value
            assertThat(configuration.tlsMutualAuthenticationCertificateChain(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.tlsMutualAuthenticationCertificateChain(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.tlsMutualAuthenticationCertificateChain(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.tlsMutualAuthenticationCertificateChain"), equalTo(firstPath));
            assertThat(configuration.tlsMutualAuthenticationCertificateChain(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.tlsMutualAuthenticationCertificateChain(secondPath);

            // then - getter
            assertThat(configuration.tlsMutualAuthenticationCertificateChain(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.tlsMutualAuthenticationCertificateChain(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyTLSX509CertificatesTrustManagerType() {
        ForwardProxyTLSX509CertificatesTrustManager original = ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();
        try {
            // then - default value
            assertThat(configuration.forwardProxyTLSX509CertificatesTrustManagerType(), equalTo(ForwardProxyTLSX509CertificatesTrustManager.ANY));

            // when - system property setter
            ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM);

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType(), equalTo(ForwardProxyTLSX509CertificatesTrustManager.JVM));
            assertThat(System.getProperty("mockserver.forwardProxyTLSX509CertificatesTrustManagerType"), equalTo("JVM"));
            assertThat(configuration.forwardProxyTLSX509CertificatesTrustManagerType(), equalTo(ForwardProxyTLSX509CertificatesTrustManager.JVM));

            // when - setter
            configuration.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM);

            // then - getter
            assertThat(configuration.forwardProxyTLSX509CertificatesTrustManagerType(), equalTo(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM));
        } finally {
            ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyTLSCustomTrustX509Certificates() {
        String original = ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates();
        try {
            // then - default value
            assertThat(configuration.forwardProxyTLSCustomTrustX509Certificates(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.forwardProxyTLSCustomTrustX509Certificates"), equalTo(firstPath));
            assertThat(configuration.forwardProxyTLSCustomTrustX509Certificates(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.forwardProxyTLSCustomTrustX509Certificates(secondPath);

            // then - getter
            assertThat(configuration.forwardProxyTLSCustomTrustX509Certificates(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyPrivateKey() {
        String original = ConfigurationProperties.forwardProxyPrivateKey();
        try {
            // then - default value
            assertThat(configuration.forwardProxyPrivateKey(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.forwardProxyPrivateKey(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyPrivateKey(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.forwardProxyPrivateKey"), equalTo(firstPath));
            assertThat(configuration.forwardProxyPrivateKey(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.forwardProxyPrivateKey(secondPath);

            // then - getter
            assertThat(configuration.forwardProxyPrivateKey(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.forwardProxyPrivateKey(original);
        }
    }

    @Test
    public void shouldSetAndGetForwardProxyCertificateChain() {
        String original = ConfigurationProperties.forwardProxyCertificateChain();
        try {
            // then - default value
            assertThat(configuration.forwardProxyCertificateChain(), equalTo(""));

            // when - system property setter
            String firstPath = tempFilePath();
            ConfigurationProperties.forwardProxyCertificateChain(firstPath);

            // then - system property getter
            assertThat(ConfigurationProperties.forwardProxyCertificateChain(), equalTo(firstPath));
            assertThat(System.getProperty("mockserver.forwardProxyCertificateChain"), equalTo(firstPath));
            assertThat(configuration.forwardProxyCertificateChain(), equalTo(firstPath));

            // when - setter
            String secondPath = tempFilePath();
            configuration.forwardProxyCertificateChain(secondPath);

            // then - getter
            assertThat(configuration.forwardProxyCertificateChain(), equalTo(secondPath));
        } finally {
            ConfigurationProperties.forwardProxyCertificateChain(original);
        }
    }

    @Test
    public void shouldSetAndGetAddSubjectAlternativeName() {
        Set<String> original = ConfigurationProperties.sslSubjectAlternativeNameDomains();
        try {
            // then - default value
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("localhost")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(false));

            // when - setter existing value
            configuration.addSubjectAlternativeName("localhost");

            // then - still default values
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("localhost")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(false));

            // when - setter
            configuration.addSubjectAlternativeName("mock-server.co.uk");

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of("localhost", "mock-server.co.uk")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(true));

            // when - clear
            configuration.rebuildServerTLSContext(false);
            configuration.clearSslSubjectAlternativeNameDomains();

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameDomains(), equalTo(ImmutableSet.of()));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(true));
        } finally {
            ConfigurationProperties.sslSubjectAlternativeNameDomains(original);
        }
    }

    @Test
    public void shouldSetAndGetAddSslSubjectAlternativeNameIps() {
        Set<String> original = ConfigurationProperties.sslSubjectAlternativeNameIps();
        try {
            // then - default value
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("127.0.0.1", "0.0.0.0")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(false));

            // when - setter existing value
            configuration.addSubjectAlternativeName("127.0.0.1");

            // then - still default values
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("127.0.0.1", "0.0.0.0")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(false));

            // when - setter
            configuration.addSubjectAlternativeName("1.2.3.4");

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of("127.0.0.1", "0.0.0.0", "1.2.3.4")));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(true));

            // when - clear
            configuration.rebuildServerTLSContext(false);
            configuration.clearSslSubjectAlternativeNameIps();

            // then - getter
            assertThat(configuration.sslSubjectAlternativeNameIps(), equalTo(ImmutableSet.of()));
            assertThat(configuration.rebuildServerTLSContext(), equalTo(true));
        } finally {
            ConfigurationProperties.sslSubjectAlternativeNameIps(original);
        }
    }

}