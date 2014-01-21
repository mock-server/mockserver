package org.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.PortFactory;
import org.vertx.java.platform.impl.cli.Starter;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerVertxIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int port = PortFactory.findFreePort();
    private final static int securePort = PortFactory.findFreePort();
    private final static Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            System.setProperty("mockserver.serverPort", "" + port);
            System.setProperty("mockserver.serverSecurePort", "" + securePort);
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });

    @BeforeClass
    public static void startServer() throws InterruptedException {
        vertxServer.start();
        vertxServer.join(TimeUnit.SECONDS.toMillis(1));

        // start client
        mockServerClient = new MockServerClient("localhost", port, servletContext);
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public int getSecurePort() {
        return securePort;
    }

    @AfterClass
    public static void stopServer() {
        new ApacheHttpClient().sendRequest(
                new HttpRequest()
                        .withMethod("PUT")
                        .withURL("http://localhost:" + port + "/stop")
                        .withPath("/stop")
        );
    }
}
