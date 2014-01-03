package org.mockserver;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.PortFactory;
import org.vertx.java.platform.impl.cli.Starter;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerVertxIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int port = PortFactory.findFreePort();
    private final int securePort = PortFactory.findFreePort();
    private final Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            System.setProperty("mockserver.port", "" + getPort());
            System.setProperty("mockserver.securePort", "" + getSecurePort());
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });

    @Before
    public void startServer() throws InterruptedException {
        vertxServer.start();
        vertxServer.join(TimeUnit.SECONDS.toMillis(1));
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public int getSecurePort() {
        return securePort;
    }

    @After
    public void stopServer() {
        makeRequest(
                new HttpRequest()
                        .withMethod("PUT")
                        .withURL("http://localhost:" + getPort() + "/stop")
                        .withPath("/stop")
        );
    }
}
