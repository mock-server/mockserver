package org.mockserver.netty.integration.mock;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.integration.ShadedJarRunner;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;
import org.slf4j.event.Level;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ExtendedShadedJarMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static final int mockServerPort = PortFactory.findFreePort();

    protected boolean supportsHTTP2() {
        // TODO(jamesdbloom) support copying native content into the no-dependencies jar
        return SslProvider.isAlpnSupported(SslContext.defaultServerProvider()) || SslProvider.isAlpnSupported(SslProvider.JDK) || SslProvider.isAlpnSupported(SslProvider.OPENSSL);
    }

    @BeforeClass
    public static void startServerUsingShadedJar() {
        mockServerClient = ShadedJarRunner.startServerUsingShadedJar(mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        if (MockServerLogger.isEnabled(Level.DEBUG)) {
            ShadedJarRunner.printOutputStreams();
        }
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

}
